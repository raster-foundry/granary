module Main exposing (main)

import Browser
import Browser.Navigation as Nav
import Dict as Dict
import Element
    exposing
        ( Element
        , column
        , fill
        , fillPortion
        , height
        , padding
        , row
        , spacing
        , text
        , width
        )
import Element.Font as Font
import Element.Input as Input
import Framework.Card as Card
import Http as Http
import HttpBuilder as B
import Json.Decode as JD
import Json.Decode.Extra as JDE
import Json.Encode as JE
import Json.Schema as Schema
import Json.Schema.Definitions as Schema
    exposing
        ( Schema(..)
        , SingleType(..)
        , Type(..)
        )
import Json.Schema.Validation as Validation
import Maybe.Extra exposing (orElse)
import Pages.TaskList exposing (TaskListModel, decoderGranaryTask, emptyFormValues, emptyTaskListModel, showType, taskList)
import Result
import Set exposing (Set)
import String
import Styled exposing (styledPrimaryText, styledSecondaryText, submitButton, textInput)
import Time
import Types exposing (GranaryToken, Msg(..), PaginatedResponse)
import Url
import Url.Parser as Parser exposing ((<?>))
import Url.Parser.Query as Query
import Urls exposing (apiExecutionsUrl)
import Uuid as Uuid


type alias Model =
    { url : Url.Url
    , taskListModel : Maybe TaskListModel
    , key : Nav.Key
    , route : Route
    , granaryExecutions : List GranaryExecution
    , executionNameSearch : Maybe String
    , selectedExecutions : Set String
    , secrets : Maybe GranaryToken
    , secretsUnsubmitted : Maybe GranaryToken
    }


type alias StacAsset =
    { href : String
    , title : Maybe String
    , description : Maybe String
    , roles : List String
    , mediaType : String
    }


type alias GranaryExecution =
    { id : Uuid.Uuid
    , taskId : Uuid.Uuid
    , invokedAt : Time.Posix
    , statusReason : Maybe String
    , results : List StacAsset
    , webhookId : Maybe Uuid.Uuid
    , name : String
    }


type alias ExecutionCreate =
    { name : String
    , taskId : Uuid.Uuid
    , arguments : JD.Value
    }


encodeExecutionCreate : ExecutionCreate -> JE.Value
encodeExecutionCreate executionCreate =
    JE.object
        [ ( "taskId", Uuid.encode executionCreate.taskId )
        , ( "arguments", executionCreate.arguments )
        , ( "name", JE.string executionCreate.name )
        ]


decoderStacAsset : JD.Decoder StacAsset
decoderStacAsset =
    JD.map5
        StacAsset
        (JD.field "href" JD.string)
        (JD.field "title" <| JD.maybe JD.string)
        (JD.field "description" <| JD.maybe JD.string)
        (JD.field "roles" <| JD.list JD.string)
        (JD.field "type" <| JD.string)


decoderGranaryExecution : JD.Decoder GranaryExecution
decoderGranaryExecution =
    JD.map7
        GranaryExecution
        (JD.field "id" Uuid.decoder)
        (JD.field "taskId" Uuid.decoder)
        (JD.field "invokedAt" JDE.datetime)
        (JD.field "statusReason" <| JD.maybe JD.string)
        (JD.field "results" <| JD.list decoderStacAsset)
        (JD.field "webhookId" <| JD.maybe Uuid.decoder)
        (JD.field "name" JD.string)


paginatedDecoder : JD.Decoder a -> JD.Decoder (PaginatedResponse a)
paginatedDecoder ofDecoder =
    JD.map3
        PaginatedResponse
        (JD.field "pageSize" JD.int)
        (JD.field "page" JD.int)
        (JD.field "results" <| JD.list ofDecoder)


type Route
    = Login
    | TaskList
    | ExecutionList (Maybe Uuid.Uuid)


routeParser : Parser.Parser (Route -> a) a
routeParser =
    let
        uuidQueryParam strings =
            List.head strings |> Maybe.andThen Uuid.fromString
    in
    Parser.oneOf
        [ Parser.map ExecutionList
            (Parser.s "executions" <?> Query.custom "taskId" uuidQueryParam)
        , Parser.map TaskList (Parser.s "tasks")
        ]


init : () -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init _ url key =
    ( { url = url
      , key = key
      , route = Login
      , granaryExecutions = []
      , executionNameSearch = Nothing
      , selectedExecutions = Set.empty
      , secrets = Nothing
      , secretsUnsubmitted = Nothing
      , taskListModel = Nothing
      }
    , Nav.pushUrl key (Url.toString url)
    )



---- UPDATE ----


fetchTasks : GranaryToken -> Cmd.Cmd Msg
fetchTasks token =
    B.get "/api/tasks"
        |> B.withExpect (Http.expectJson GotTasks (paginatedDecoder decoderGranaryTask))
        |> B.withBearerToken token
        |> B.request


fetchExecutions : Maybe String -> Maybe Uuid.Uuid -> GranaryToken -> Cmd.Cmd Msg
fetchExecutions namesLike taskId token =
    apiExecutionsUrl namesLike taskId
        |> B.get
        |> B.withExpect (Http.expectJson (GotExecutions taskId) (paginatedDecoder decoderGranaryExecution))
        |> B.withBearerToken token
        |> B.request


postExecution : GranaryToken -> ExecutionCreate -> Cmd.Cmd Msg
postExecution token executionCreate =
    B.post "/api/executions"
        |> B.withJsonBody (encodeExecutionCreate executionCreate)
        |> B.withBearerToken token
        |> B.withExpect (Http.expectJson CreatedExecution decoderGranaryExecution)
        |> B.request


maybePostExecution : Maybe GranaryToken -> ExecutionCreate -> Cmd.Cmd Msg
maybePostExecution tokenM executionCreate =
    tokenM
        |> Maybe.map (\token -> postExecution token executionCreate)
        |> Maybe.withDefault Cmd.none


updateTaskListModel : (TaskListModel -> TaskListModel) -> Model -> Model
updateTaskListModel f mod =
    let
        baseTaskListModel =
            mod.taskListModel

        updated =
            Maybe.map f baseTaskListModel
    in
    { mod | taskListModel = updated }


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        UrlChanged url ->
            let
                routeResult =
                    Parser.parse routeParser url

                getCmd f t =
                    Maybe.map f t
                        |> Maybe.withDefault Cmd.none

                cmd =
                    case ( routeResult, model.secrets ) of
                        ( Just TaskList, modelToken ) ->
                            getCmd fetchTasks modelToken

                        ( Just (ExecutionList taskId), modelToken ) ->
                            getCmd (fetchExecutions Nothing taskId) modelToken

                        -- we have a token available, but this is not a url we know how to handle
                        -- possibly premature for actually storing the token in local storage
                        ( _, Just _ ) ->
                            Nav.pushUrl model.key "/tasks"

                        ( _, Nothing ) ->
                            Cmd.none
            in
            ( { model | url = url }, cmd )

        Navigation urlRequest ->
            case urlRequest of
                Browser.Internal url ->
                    ( model, Nav.pushUrl model.key (Url.toString url) )

                Browser.External href ->
                    ( model, Nav.load href )

        TaskSelect task ->
            ( updateTaskListModel
                (\tlm ->
                    { tlm
                        | selectedTask = Just task
                        , taskValidationErrors = Dict.empty
                        , formValues = emptyFormValues
                        , activeSchema = Just task.validator
                    }
                )
                model
            , Cmd.none
            )

        GotTasks (Ok tasks) ->
            let
                updatedTlm =
                    updateTaskListModel (always { emptyTaskListModel | tasks = tasks.results }) model
            in
            ( { updatedTlm | route = TaskList, secretsUnsubmitted = Nothing }, Cmd.none )

        GotTasks (Err _) ->
            ( model, Cmd.none )

        GotExecutions taskId (Ok executionsPage) ->
            ( { model | route = ExecutionList taskId, granaryExecutions = executionsPage.results }, Cmd.none )

        GotExecutions _ (Err _) ->
            ( model, Nav.pushUrl model.key "/" )

        TokenInput s ->
            ( { model | secretsUnsubmitted = Just s }, Cmd.none )

        TokenSubmit ->
            ( { model | secrets = model.secretsUnsubmitted }
            , Nav.pushUrl model.key "/tasks"
            )

        CreatedExecution (Ok _) ->
            ( { model | taskListModel = Nothing }
            , Nav.pushUrl model.key
                ("/executions?taskId="
                    ++ (model.taskListModel
                            |> Maybe.andThen .selectedTask
                            |> Maybe.map (Uuid.toString << .id)
                            |> Maybe.withDefault ""
                       )
                )
            )

        CreatedExecution _ ->
            ( model, Cmd.none )

        ValidateWith validateOpts ->
            let
                validation =
                    case validateOpts.fieldValue of
                        Result.Ok jsonValue ->
                            Schema.validateValue
                                { applyDefaults = True }
                                jsonValue
                                (ObjectSchema validateOpts.schema)

                        Result.Err _ ->
                            [ { jsonPointer = Validation.JsonPointer "" [ validateOpts.fieldName ]
                              , details = Validation.InvalidType ("Expected " ++ showType validateOpts.schema.type_ ++ " 😟")
                              }
                            ]
                                |> Result.Err
            in
            case validation of
                Result.Ok _ ->
                    let
                        formValues =
                            model.taskListModel
                                |> Maybe.map .formValues
                                |> Maybe.withDefault emptyFormValues

                        newFormValues =
                            { formValues
                                | fromSchema =
                                    Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                        formValues.fromSchema
                            }

                        baseValidationErrors =
                            model.taskListModel
                                |> Maybe.map .taskValidationErrors
                                |> Maybe.withDefault Dict.empty

                        updatedTlm =
                            updateTaskListModel
                                (\tlm ->
                                    { tlm
                                        | taskValidationErrors = Dict.remove validateOpts.fieldName baseValidationErrors
                                        , formValues = newFormValues
                                    }
                                )
                                model
                    in
                    ( updatedTlm
                    , Cmd.none
                    )

                Result.Err errs ->
                    let
                        formValues =
                            model.taskListModel
                                |> Maybe.map .formValues
                                |> Maybe.withDefault emptyFormValues

                        newFormValues =
                            { formValues
                                | fromSchema =
                                    Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                        formValues.fromSchema
                            }

                        updatedTlm =
                            updateTaskListModel
                                (\tlm ->
                                    { tlm
                                        | taskValidationErrors =
                                            Dict.union
                                                (Dict.singleton
                                                    validateOpts.fieldName
                                                    errs
                                                )
                                                tlm.taskValidationErrors
                                        , formValues = newFormValues
                                    }
                                )
                                model
                    in
                    ( updatedTlm
                    , Cmd.none
                    )

        CreateExecution executionCreate ->
            ( model, maybePostExecution model.secrets executionCreate )

        ToggleShowAssets executionId ->
            let
                stringExecutionId =
                    Uuid.toString executionId

                selectedExecutions =
                    model.selectedExecutions
            in
            ( { model
                | selectedExecutions =
                    if Set.member stringExecutionId selectedExecutions then
                        Set.remove stringExecutionId selectedExecutions

                    else
                        Set.insert stringExecutionId selectedExecutions
              }
            , Cmd.none
            )

        SearchExecutionName s ->
            ( { model | executionNameSearch = Just s }
            , model.secrets
                |> Maybe.map
                    (fetchExecutions (Just s)
                        (model.taskListModel
                            |> Maybe.andThen .selectedTask
                            |> Maybe.map .id
                        )
                    )
                |> Maybe.withDefault Cmd.none
            )

        AddTokenParam token ->
            let
                baseUrl =
                    model.url

                hasToken =
                    baseUrl.query |> Maybe.withDefault "" |> String.contains "token"

                isEmpty =
                    baseUrl.query |> Maybe.withDefault "" |> String.isEmpty

                tokenQp =
                    "token=" ++ token

                newQp =
                    case ( hasToken, isEmpty ) of
                        -- technically, (True, False) can't happen
                        ( True, _ ) ->
                            baseUrl.query
                                |> Maybe.withDefault ""

                        ( False, False ) ->
                            tokenQp
                                |> (++) ((baseUrl.query |> Maybe.withDefault "") ++ "&")

                        ( False, True ) ->
                            tokenQp
            in
            ( model, Nav.pushUrl model.key (Url.toString { baseUrl | query = Just newQp }) )

        GoHome ->
            ( { model | taskListModel = Nothing }, Nav.pushUrl model.key "/tasks" )

        NameExecution s ->
            let
                formValues =
                    model.taskListModel
                        |> Maybe.map .formValues
                        |> Maybe.withDefault emptyFormValues

                newFormValues =
                    { formValues | executionName = Just s }

                updatedTlm =
                    updateTaskListModel (\tlm -> { tlm | formValues = newFormValues }) model
            in
            ( updatedTlm, Cmd.none )



---- VIEW ----


logo : List (Element.Attribute msg) -> Int -> Element msg
logo attrs maxSize =
    Element.image
        ([ fillPortion 1 |> Element.minimum 100 |> Element.maximum maxSize |> height
         , padding 10
         ]
            ++ attrs
        )
        { src = "logo.svg"
        , description = "Granary logo"
        }


toEmoji : GranaryExecution -> String
toEmoji execution =
    case ( execution.statusReason, execution.results ) of
        ( Just _, _ ) ->
            "❌"

        ( _, _ :: _ ) ->
            "✅"

        _ ->
            "🏃\u{200D}♀️"


executionAssetsList : List StacAsset -> List (Element Msg)
executionAssetsList =
    List.map
        (\asset ->
            Element.link []
                { url = asset.href
                , label =
                    styledSecondaryText [ Font.underline ]
                        (asset.title
                            |> orElse asset.description
                            |> Maybe.withDefault
                                (asset.roles |> List.intersperse ", " |> String.concat)
                        )
                }
        )


logoTop : List (Element Msg) -> Element Msg
logoTop rest =
    column [ width fill, Element.centerX ] <|
        [ row [ Element.centerX, Element.maximum 400 fill |> width ] <|
            [ column [ width (fillPortion 9), height fill ]
                [ logo
                    [ width fill ]
                    100
                ]
            , column [ width (fillPortion 1), height fill ]
                [ row [ Element.centerY ]
                    [ homeLink
                    ]
                ]
            ]
        ]
            ++ rest


executionAssets : Bool -> GranaryExecution -> List (Element Msg)
executionAssets showAssets execution =
    if List.isEmpty execution.results then
        []

    else
        row []
            [ Input.button []
                { onPress = ToggleShowAssets execution.id |> Just
                , label = text "➕"
                }
            , styledPrimaryText [] " Show assets"
            ]
            :: (if showAssets then
                    executionAssetsList execution.results

                else
                    []
               )


executionCard : Bool -> GranaryExecution -> Element Msg
executionCard showAssets execution =
    [ column
        (width
            (fill
                |> Element.minimum 350
                |> Element.maximum 400
            )
            :: spacing 5
            :: Card.simple
        )
        ([ row [] [ styledPrimaryText [] execution.name ]
         , row [] [ styledPrimaryText [] ("Status: " ++ toEmoji execution) ]
         ]
            ++ executionAssets showAssets execution
        )
    ]
        |> row [ width fill ]


nameSearchInput : Maybe String -> Element Msg
nameSearchInput currValue =
    textInput
        [ width fill ]
        SearchExecutionName
        currValue
        "Name like"
        "Search"
        |> List.singleton
        |> row [ width fill ]


homeLink : Element Msg
homeLink =
    Input.button []
        { onPress = Just GoHome
        , label = text "🏠"
        }


executionList : Model -> Element Msg
executionList model =
    let
        showAssets execution =
            Set.member (Uuid.toString execution.id) model.selectedExecutions

        card execution =
            executionCard (showAssets execution) execution
    in
    logoTop
        [ nameSearchInput model.executionNameSearch
            :: (model.granaryExecutions
                    |> List.map card
               )
            |> column [ Element.centerX, spacing 10, padding 15 ]
        ]


loginPage : Model -> Element Msg
loginPage model =
    column [ spacing 3, Element.centerX, Element.centerY, width Element.shrink ]
        [ row [ width fill ] [ logo [] 200 ]
        , row [ width fill ]
            [ textInput [] TokenInput model.secretsUnsubmitted "Enter a token" "Token input"
            ]
        , row [ width fill ]
            [ submitButton (not << String.isEmpty)
                (Maybe.withDefault "" model.secretsUnsubmitted)
                "Please enter a token"
                TokenSubmit
            ]
        ]


view : Model -> Browser.Document Msg
view model =
    case ( model.route, model.secrets, model.taskListModel ) of
        ( TaskList, Just _, Just tlm ) ->
            let
                taskListBody =
                    taskList tlm
            in
            { title = "Available Tasks"
            , body = [ Element.layout [] taskListBody ]
            }

        ( ExecutionList _, Just _, _ ) ->
            { title = "Execution list"
            , body =
                [ Element.layout [] <| executionList model
                ]
            }

        _ ->
            { title = "Granary Model Dashboard"
            , body =
                [ Element.layout [] <| loginPage model
                ]
            }



---- PROGRAM ----


main : Program () Model Msg
main =
    Browser.application
        { view = view
        , init = init
        , update = update
        , subscriptions = always Sub.none
        , onUrlRequest = Navigation
        , onUrlChange = UrlChanged
        }
