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
import Element.Input as Input
import File
import File.Select as Select
import GeoJson as GeoJson
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
import Pages.ExecutionList exposing (ExecutionListModel, emptyExecutionListModel, executionList)
import Pages.TaskList exposing (TaskListModel, decoderGranaryTask, emptyFormValues, emptyTaskListModel, setInputState, showType, taskList)
import Result
import Set as Set
import String
import Styled exposing (submitButton, textInput)
import Task as Task
import Types exposing (ExecutionCreate, ExecutionCreateError(..), GranaryExecution, GranaryToken, InputEvent(..), Msg(..), PaginatedResponse, StacAsset)
import Url
import Url.Parser as Parser exposing ((<?>))
import Url.Parser.Query as Query
import Urls exposing (apiExecutionsUrl)
import Uuid as Uuid


type alias Model =
    { url : Url.Url
    , key : Nav.Key
    , route : Route
    , secrets : Maybe GranaryToken
    , secretsUnsubmitted : Maybe GranaryToken
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
    | TaskList TaskListModel
    | ExecutionList ExecutionListModel


routeParser : Parser.Parser (Route -> a) a
routeParser =
    let
        uuidQueryParam strings =
            List.head strings |> Maybe.andThen Uuid.fromString
    in
    Parser.oneOf
        [ Parser.map (\maybeId -> ExecutionList { emptyExecutionListModel | forTask = maybeId })
            (Parser.s "executions" <?> Query.custom "taskId" uuidQueryParam)
        , Parser.map (TaskList emptyTaskListModel) (Parser.s "tasks")
        ]


init : () -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init _ url key =
    ( { url = url
      , key = key
      , route = Login
      , secrets = Nothing
      , secretsUnsubmitted = Nothing
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


updateExecutionListModel : (ExecutionListModel -> ExecutionListModel) -> Route -> Maybe ExecutionListModel
updateExecutionListModel f route =
    case route of
        ExecutionList mod ->
            f mod |> Just

        _ ->
            Nothing


updateTaskListModel : (TaskListModel -> TaskListModel) -> Route -> Maybe TaskListModel
updateTaskListModel f route =
    case route of
        TaskList mod ->
            f mod |> Just

        _ ->
            Nothing


getSelectedTask : Model -> Maybe Uuid.Uuid
getSelectedTask model =
    case model.route of
        TaskList tlm ->
            tlm.selectedTask |> Maybe.map .id

        ExecutionList elm ->
            elm.forTask

        _ ->
            Nothing


getSelectedExecutions : Model -> Maybe (Set.Set String)
getSelectedExecutions model =
    case model.route of
        ExecutionList elm ->
            elm.selectedExecutions |> Just

        _ ->
            Nothing


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
                        ( Just (TaskList _), modelToken ) ->
                            getCmd fetchTasks modelToken

                        ( Just (ExecutionList elm), modelToken ) ->
                            getCmd (fetchExecutions Nothing elm.forTask) modelToken

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
            let
                newRoute =
                    updateTaskListModel
                        (\tlm ->
                            { tlm
                                | selectedTask = Just task
                                , taskValidationErrors = Dict.empty
                                , formValues = emptyFormValues
                                , activeSchema = Just task.validator
                            }
                        )
                        model.route
                        |> Maybe.map TaskList
                        |> Maybe.withDefault Login
            in
            ( { model | route = newRoute }
            , Cmd.none
            )

        GotTasks (Ok tasks) ->
            ( { model
                | route = TaskList { emptyTaskListModel | tasks = tasks.results }
                , secretsUnsubmitted = Nothing
              }
            , Cmd.none
            )

        GotTasks (Err _) ->
            ( model, Cmd.none )

        GotExecutions taskId (Ok executionsPage) ->
            let
                withExecutions =
                    emptyExecutionListModel
                        |> (\elm ->
                                { elm
                                    | executions = executionsPage.results
                                    , forTask = taskId
                                }
                           )
            in
            ( { model
                | route = ExecutionList withExecutions
              }
            , Cmd.none
            )

        GotExecutions _ (Err _) ->
            ( model, Nav.pushUrl model.key "/" )

        TokenInput s ->
            ( { model | secretsUnsubmitted = Just s }, Cmd.none )

        TokenSubmit ->
            ( { model | secrets = model.secretsUnsubmitted }
            , Nav.pushUrl model.key "/tasks"
            )

        CreatedExecution (Ok _) ->
            ( model
            , Nav.pushUrl model.key
                ("/executions?taskId="
                    ++ (getSelectedTask model
                            |> Maybe.map Uuid.toString
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
            case ( validation, model.route ) of
                ( Result.Ok _, TaskList tlm ) ->
                    let
                        formValues =
                            tlm.formValues

                        newFormValues =
                            { formValues
                                | fromSchema =
                                    Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                        formValues.fromSchema
                            }

                        baseValidationErrors =
                            tlm.taskValidationErrors

                        updatedTlm =
                            { tlm
                                | taskValidationErrors = Dict.remove validateOpts.fieldName baseValidationErrors
                                , formValues = newFormValues
                            }
                    in
                    ( { model | route = TaskList updatedTlm }
                    , Cmd.none
                    )

                ( Result.Err errs, TaskList tlm ) ->
                    let
                        formValues =
                            tlm.formValues

                        newFormValues =
                            { formValues
                                | fromSchema =
                                    Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                        formValues.fromSchema
                            }

                        updatedTlm =
                            { tlm
                                | taskValidationErrors =
                                    Dict.union
                                        (Dict.singleton
                                            validateOpts.fieldName
                                            (SchemaError errs)
                                        )
                                        tlm.taskValidationErrors
                                , formValues = newFormValues
                            }
                    in
                    ( { model | route = TaskList updatedTlm }
                    , Cmd.none
                    )

                _ ->
                    ( model, Cmd.none )

        CreateExecution executionCreate ->
            ( model, maybePostExecution model.secrets executionCreate )

        ToggleShowAssets executionId ->
            let
                stringExecutionId =
                    Uuid.toString executionId

                selectedExecutions =
                    getSelectedExecutions model
                        |> Maybe.withDefault Set.empty

                newSelected =
                    if Set.member stringExecutionId selectedExecutions then
                        Set.remove stringExecutionId selectedExecutions

                    else
                        Set.insert stringExecutionId selectedExecutions

                updatedElm =
                    updateExecutionListModel (\elm -> { elm | selectedExecutions = newSelected }) model.route
                        |> Maybe.map ExecutionList
                        |> Maybe.withDefault Login
            in
            ( { model | route = updatedElm }
            , Cmd.none
            )

        SearchExecutionName s ->
            let
                updatedElm =
                    updateExecutionListModel (\elm -> { elm | executionNameSearch = Just s }) model.route

                newRoute =
                    updatedElm |> Maybe.map ExecutionList |> Maybe.withDefault Login
            in
            ( { model | route = newRoute }
            , model.secrets
                |> Maybe.map
                    (fetchExecutions (Just s)
                        (updatedElm
                            |> Maybe.andThen .forTask
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
            ( { model | route = TaskList emptyTaskListModel }, Nav.pushUrl model.key "/tasks" )

        NameExecution s ->
            let
                formValues =
                    case model.route of
                        TaskList tlm ->
                            tlm.formValues

                        _ ->
                            emptyFormValues

                newFormValues =
                    { formValues | executionName = Just s }

                updatedTlm =
                    updateTaskListModel (\tlm -> { tlm | formValues = newFormValues }) model.route
                        |> Maybe.map TaskList
                        |> Maybe.withDefault Login
            in
            ( { model | route = updatedTlm }, Cmd.none )

        GotFiles head _ ->
            ( model, Task.perform GeoJsonData (File.toString head) )

        GeoJsonData data ->
            case ( JD.decodeString GeoJson.decoder data, model.route ) of
                ( Result.Ok taskGrid, TaskList tlm ) ->
                    let
                        formValues =
                            tlm.formValues

                        addition =
                            Dict.singleton "TASK_GRID" (Result.Ok (GeoJson.encode taskGrid))

                        newTaskValidationErrors =
                            Dict.remove "TASK_GRID" tlm.taskValidationErrors

                        newFormValues =
                            { formValues | fromSchema = Dict.union addition formValues.fromSchema }

                        updated =
                            { tlm
                                | formValues = newFormValues
                                , taskValidationErrors = newTaskValidationErrors
                            }
                    in
                    ( { model | route = TaskList updated }, Cmd.none )

                ( Result.Ok taskGrid, _ ) ->
                    let
                        formValues =
                            { emptyFormValues
                                | fromSchema = Dict.singleton "TASK_GRID" (Result.Ok (GeoJson.encode taskGrid))
                            }
                    in
                    ( { model
                        | route = TaskList { emptyTaskListModel | formValues = formValues }
                      }
                    , Cmd.none
                    )

                ( Result.Err err, TaskList tlm ) ->
                    let
                        validationErrors =
                            tlm.taskValidationErrors

                        addition =
                            Dict.singleton "TASK_GRID" (DecodingError err)

                        updated =
                            { tlm | taskValidationErrors = Dict.union addition validationErrors }
                    in
                    ( { model | route = TaskList updated }, Cmd.none )

                ( Result.Err err, _ ) ->
                    let
                        validationErrors =
                            Dict.singleton "TASK_GRID" (DecodingError err)

                        updated =
                            { emptyTaskListModel | taskValidationErrors = validationErrors }
                    in
                    ( { model | route = TaskList updated }, Cmd.none )

        GeoJsonInputMouseInteraction event ->
            let
                tlm =
                    case model.route of
                        TaskList taskListModel ->
                            taskListModel

                        _ ->
                            emptyTaskListModel

                cmd =
                    case event of
                        Pick ->
                            Select.files
                                [ "application/geo+json", "application/json" ]
                                GotFiles

                        _ ->
                            Cmd.none
            in
            ( { model | route = TaskList (setInputState event tlm) }, cmd )



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


homeLink : Element Msg
homeLink =
    Input.button []
        { onPress = Just GoHome
        , label = text "🏠"
        }


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
                TokenSubmit
            ]
        ]


view : Model -> Browser.Document Msg
view model =
    case ( model.route, model.secrets ) of
        ( TaskList tlm, Just _ ) ->
            let
                taskListBody =
                    taskList tlm
            in
            { title = "Available Tasks"
            , body = [ Element.layout [] (logoTop [ taskListBody ]) ]
            }

        ( ExecutionList elm, Just _ ) ->
            { title = "Execution list"
            , body =
                [ Element.layout [] <| logoTop [ executionList elm ]
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
