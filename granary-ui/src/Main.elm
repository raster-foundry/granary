module Main exposing (main)

import Browser
import Browser.Navigation as Nav
import Dict exposing (Dict)
import Element
    exposing
        ( Element
        , column
        , el
        , fill
        , fillPortion
        , height
        , link
        , padding
        , rgb255
        , row
        , spacing
        , text
        , width
        )
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Element.Input as Input
import Framework.Button as Button
import Framework.Card as Card
import Http as Http
import HttpBuilder as B
import Iso8601
import Json.Decode as JD
import Json.Decode.Extra as JDE
import Json.Encode as JE
import Json.Schema as Schema
import Json.Schema.Definitions as Schema exposing (Schema(..))
import Json.Schema.Validation as Validation
import Time
import Url
import Uuid as Uuid


type alias PaginatedResponse a =
    { page : Int
    , limit : Int
    , results : List a
    }


type alias TaskDetail =
    { executions : List GranaryExecution
    , task : GranaryTask
    , addingExecution : Bool
    , newExecution : Result (List Validation.Error) JD.Value
    , newExecutionRaw : String
    }


type alias GranaryToken =
    String


type alias Breadcrumb =
    { url : String
    , name : String
    }


type alias Model =
    { url : Url.Url
    , key : Nav.Key
    , granaryTasks : List GranaryTask
    , taskValidationErrors : Dict String (List Validation.Error)
    , formValues : Dict String String
    , selectedTask : Maybe GranaryTask
    , taskDetail : Maybe TaskDetail
    , secrets : Maybe GranaryToken
    , secretsUnsubmitted : Maybe GranaryToken
    }


type alias GranaryTask =
    { id : Uuid.Uuid
    , name : String
    , validator : Schema
    , jobDefinition : String
    , jobQueue : String
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
    }


type alias ExecutionCreate =
    { taskId : Uuid.Uuid
    , arguments : JD.Value
    }


encodeExecutionCreate : ExecutionCreate -> JE.Value
encodeExecutionCreate executionCreate =
    JE.object
        [ ( "taskId", Uuid.encode executionCreate.taskId )
        , ( "arguments", executionCreate.arguments )
        ]


decoderGranaryModel : JD.Decoder GranaryTask
decoderGranaryModel =
    JD.map5
        GranaryTask
        (JD.field "id" Uuid.decoder)
        (JD.field "name" JD.string)
        (JD.field "validator" (JD.field "schema" Schema.decoder))
        (JD.field "jobDefinition" JD.string)
        (JD.field "jobQueue" JD.string)


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
    JD.map6
        GranaryExecution
        (JD.field "id" Uuid.decoder)
        (JD.field "taskId" Uuid.decoder)
        (JD.field "invokedAt" JDE.datetime)
        (JD.field "statusReason" <| JD.maybe JD.string)
        (JD.field "results" <| JD.list decoderStacAsset)
        (JD.field "webhookId" <| JD.maybe Uuid.decoder)


paginatedDecoder : JD.Decoder a -> JD.Decoder (PaginatedResponse a)
paginatedDecoder ofDecoder =
    JD.map3
        PaginatedResponse
        (JD.field "pageSize" JD.int)
        (JD.field "page" JD.int)
        (JD.field "results" <| JD.list ofDecoder)


init : () -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init _ url key =
    ( { url = url
      , key = key
      , granaryTasks = []
      , selectedTask = Nothing
      , taskDetail = Nothing
      , taskValidationErrors = Dict.empty
      , formValues = Dict.empty
      , secrets = Nothing
      , secretsUnsubmitted = Nothing
      }
    , Cmd.none
    )



---- UPDATE ----


getExecutionCreate : TaskDetail -> Maybe ExecutionCreate
getExecutionCreate detail =
    (Result.toMaybe << .newExecution) detail
        |> Maybe.map (ExecutionCreate detail.task.id)


modelUrl : Uuid.Uuid -> String
modelUrl =
    (++) "/api/tasks/" << Uuid.toString


executionsUrl : Uuid.Uuid -> String
executionsUrl =
    (++) "/api/executions?taskId=" << Uuid.toString


fetchModels : Maybe GranaryToken -> Cmd.Cmd Msg
fetchModels token =
    token
        |> Maybe.map
            (\t ->
                B.get "/api/tasks"
                    |> B.withExpect (Http.expectJson GotTasks (paginatedDecoder decoderGranaryModel))
                    |> B.withBearerToken t
                    |> B.request
            )
        |> Maybe.withDefault Cmd.none


fetchModel : Maybe GranaryToken -> Uuid.Uuid -> Cmd.Cmd Msg
fetchModel token modelId =
    token
        |> Maybe.map
            (\t ->
                modelUrl modelId
                    |> B.get
                    |> B.withExpect (Http.expectJson GotTask decoderGranaryModel)
                    |> B.withBearerToken t
                    |> B.request
            )
        |> Maybe.withDefault Cmd.none


fetchExecutions : Maybe GranaryToken -> Uuid.Uuid -> Cmd.Cmd Msg
fetchExecutions token modelId =
    token
        |> Maybe.map
            (\t ->
                executionsUrl modelId
                    |> B.get
                    |> B.withExpect (Http.expectJson GotExecutions (paginatedDecoder decoderGranaryExecution))
                    |> B.withBearerToken t
                    |> B.request
            )
        |> Maybe.withDefault Cmd.none


postExecution : GranaryToken -> ExecutionCreate -> Cmd.Cmd Msg
postExecution token executionCreate =
    B.post "/api/executions"
        |> B.withJsonBody (encodeExecutionCreate executionCreate)
        |> B.withBearerToken token
        |> B.withExpect (Http.expectJson CreatedExecution decoderGranaryExecution)
        |> B.request


maybePostExecution : Maybe GranaryToken -> Maybe TaskDetail -> Cmd.Cmd Msg
maybePostExecution tokenM detailM =
    case ( tokenM, detailM ) of
        ( Just token, Just detail ) ->
            getExecutionCreate detail
                |> Maybe.map (postExecution token)
                |> Maybe.withDefault Cmd.none

        _ ->
            Cmd.none


type Msg
    = GotTasks (Result Http.Error (PaginatedResponse GranaryTask))
    | GotTask (Result Http.Error GranaryTask)
    | GotExecutions (Result Http.Error (PaginatedResponse GranaryExecution))
    | NewExecution Uuid.Uuid Schema
    | Navigation Browser.UrlRequest
    | UrlChanged Url.Url
    | ExecutionInput String
    | TokenInput String
    | TokenSubmit
    | ExecutionSubmit
    | CreatedExecution (Result Http.Error GranaryExecution)
    | TaskSelect GranaryTask
    | ValidateWith
        { schema : Schema
        , fieldName : String
        , fieldValue : String
        }
    | NoOp


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        UrlChanged url ->
            let
                maybeModelId =
                    String.dropLeft 1 url.path |> Uuid.fromString

                cmdM =
                    maybeModelId
                        |> Maybe.map (fetchModel model.secrets)
            in
            case cmdM of
                Nothing ->
                    ( { model | taskDetail = Nothing }, fetchModels model.secrets )

                Just cmd ->
                    ( model, cmd )

        Navigation urlRequest ->
            case urlRequest of
                Browser.Internal url ->
                    ( model, Nav.pushUrl model.key (Url.toString url) )

                Browser.External href ->
                    ( model, Nav.load href )

        GotTask (Ok granaryTask) ->
            ( { model
                | granaryTasks = []
                , taskDetail = Just <| TaskDetail [] granaryTask False (Result.Err []) "{}"
              }
            , fetchExecutions model.secrets granaryTask.id
            )

        TaskSelect task ->
            ( { model | selectedTask = Just task }
            , Cmd.none
            )

        GotTask (Err _) ->
            ( model, Nav.pushUrl model.key "/" )

        GotTasks (Ok tasks) ->
            ( { model | granaryTasks = tasks.results }, Cmd.none )

        GotTasks (Err _) ->
            ( model, Cmd.none )

        GotExecutions (Ok executions) ->
            let
                baseTaskDetail =
                    model.taskDetail

                updatedTaskDetail =
                    Maybe.map (\rec -> { rec | executions = executions.results }) baseTaskDetail
            in
            ( { model | taskDetail = updatedTaskDetail }, Cmd.none )

        GotExecutions (Err _) ->
            ( model, Cmd.none )

        NewExecution _ _ ->
            let
                baseTaskDetail =
                    model.taskDetail

                updatedTaskDetail =
                    Maybe.map (\rec -> { rec | addingExecution = True }) baseTaskDetail
            in
            ( { model | taskDetail = updatedTaskDetail }, Cmd.none )

        ExecutionInput s ->
            let
                baseTaskDetail =
                    model.taskDetail

                valueDecodeResult =
                    JD.decodeString JD.value s
                        |> Result.mapError
                            (always
                                [ { details = Validation.AlwaysFail
                                  , jsonPointer =
                                        { ns = ""
                                        , path = []
                                        }
                                  }
                                ]
                            )

                updatedTaskDetail =
                    baseTaskDetail
                        |> Maybe.map
                            (\rec ->
                                { rec
                                    | newExecutionRaw = s
                                    , newExecution =
                                        valueDecodeResult
                                            |> Result.andThen
                                                (\value ->
                                                    Schema.validateValue { applyDefaults = True }
                                                        value
                                                        rec.task.validator
                                                )
                                }
                            )
            in
            ( { model | taskDetail = updatedTaskDetail }, Cmd.none )

        TokenInput s ->
            ( { model | secretsUnsubmitted = Just s }, Cmd.none )

        TokenSubmit ->
            ( { model | secrets = model.secretsUnsubmitted, secretsUnsubmitted = Nothing }
            , fetchModels model.secretsUnsubmitted
            )

        CreatedExecution (Ok _) ->
            ( model
            , Nav.pushUrl model.key
                ("/"
                    ++ (model.taskDetail
                            |> Maybe.map (Uuid.toString << .id << .task)
                            |> Maybe.withDefault ""
                       )
                )
            )

        CreatedExecution _ ->
            ( model, Cmd.none )

        ExecutionSubmit ->
            ( model, maybePostExecution model.secrets model.taskDetail )

        NoOp ->
            ( model, Cmd.none )

        ValidateWith validateOpts ->
            let
                validation =
                    Schema.validateValue
                        { applyDefaults = True }
                        (JE.string
                            validateOpts.fieldValue
                        )
                        validateOpts.schema
            in
            case validation of
                Result.Ok _ ->
                    ( { model
                        | taskValidationErrors =
                            Dict.remove validateOpts.fieldName
                                model.taskValidationErrors
                        , formValues =
                            Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                model.formValues
                      }
                    , Cmd.none
                    )

                Result.Err errs ->
                    ( { model
                        | taskValidationErrors =
                            Dict.union
                                (Dict.singleton
                                    validateOpts.fieldName
                                    errs
                                )
                                model.taskValidationErrors
                        , formValues =
                            Dict.union (Dict.singleton validateOpts.fieldName validateOpts.fieldValue)
                                model.formValues
                      }
                    , Cmd.none
                    )



---- VIEW ----


fontRed : Element.Attr d m
fontRed =
    rgb255 255 0 0 |> Font.color


primary : Element.Color
primary =
    rgb255 75 59 64


secondary : Element.Color
secondary =
    rgb255 246 141 17


accent : Element.Color
accent =
    rgb255 253 233 135


styledPrimaryText : List (Element.Attribute msg) -> String -> Element msg
styledPrimaryText attrs s =
    el (Font.color primary :: attrs) (text s)


homeCrumb : Breadcrumb
homeCrumb =
    Breadcrumb "/" "Home"


taskCrumb : GranaryTask -> Breadcrumb
taskCrumb granaryModel =
    Breadcrumb ("/" ++ Uuid.toString granaryModel.id) granaryModel.name


mkHeaderName : String -> Element msg
mkHeaderName s =
    el
        [ Font.bold
        , Font.size 24
        , Border.widthEach
            { bottom = 1
            , left = 0
            , right = 0
            , top = 0
            }
        ]
        (text s)


newExecutionButton : Uuid.Uuid -> Schema.Schema -> Element Msg
newExecutionButton modelId modelSchema =
    Input.button
        [ Background.color accent
        , Element.focused [ Background.color secondary ]
        ]
        { onPress = Just (NewExecution modelId modelSchema)
        , label = Element.el [ Font.bold ] (text "New!")
        }


executionsTable : TaskDetail -> Element msg
executionsTable detail =
    Element.table [ padding 3, spacing 10, Element.alignLeft ]
        { data = detail.executions
        , columns =
            [ { header = mkHeaderName "Invocation time"
              , width = fill
              , view =
                    \execution ->
                        let
                            invokedAt =
                                execution.invokedAt
                        in
                        text <| Iso8601.fromTime invokedAt
              }
            , { header = mkHeaderName "Status"
              , width = fill
              , view =
                    \execution ->
                        text <|
                            case ( execution.statusReason, execution.results ) of
                                ( Nothing, [] ) ->
                                    "In progress"

                                ( Just _, _ ) ->
                                    "Failed"

                                ( _, _ ) ->
                                    "Succeeded"
              }
            ]
        }


navBar : List Breadcrumb -> Element Msg
navBar links =
    links
        |> List.map
            (\crumb ->
                column [ spacing 3 ]
                    [ link
                        []
                        { url = crumb.url
                        , label = text crumb.name
                        }
                    ]
            )
        |> List.intersperse (Element.text " :> ")
        |> row [ spacing 3, Background.color secondary, width fill ]


submitButton : Msg -> Element Msg
submitButton msg =
    Input.button
        (Button.simple
            ++ [ Background.color accent
               , Element.centerX
               , Border.color primary
               ]
        )
        { onPress = Just msg
        , label = styledPrimaryText [] "Submit"
        }


textInput : List (Element.Attribute msg) -> (String -> msg) -> Maybe String -> String -> String -> Element msg
textInput attrs f maybeText placeholder label =
    Input.text
        (Element.focused
            [ Border.shadow
                { offset = ( 0.1, 0.1 )
                , size = 2
                , blur = 3
                , color = secondary
                }
            ]
            :: attrs
        )
        { onChange = f
        , text = maybeText |> Maybe.withDefault ""
        , placeholder = Input.placeholder [] (text placeholder) |> Just
        , label = Input.labelHidden label
        }


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


taskCard : Maybe Uuid.Uuid -> GranaryTask -> Element Msg
taskCard selectedId task =
    row
        (Font.color primary
            :: (if Just task.id == selectedId then
                    [ Background.color accent ]

                else
                    []
               )
        )
        [ Input.button []
            { onPress = TaskSelect task |> Just
            , label = text task.name
            }
        ]
        |> Element.el Card.simple


schemaToForm : Dict String String -> Dict String (List Validation.Error) -> Schema.SubSchema -> List (Element Msg)
schemaToForm formValues errors schema =
    let
        toInput ( k, v ) =
            row []
                (textInput []
                    (\s ->
                        ValidateWith
                            { schema = v
                            , fieldName = k
                            , fieldValue = s
                            }
                    )
                    (Dict.get k formValues)
                    k
                    k
                    :: (Dict.get k errors |> Maybe.withDefault [] |> List.concatMap makeErr)
                )

        makeInput (Schema.Schemata definitions) =
            List.map toInput definitions
    in
    schema.properties
        |> Maybe.map makeInput
        |> Maybe.withDefault []


executionInput : Dict String String -> Dict String (List Validation.Error) -> GranaryTask -> List (Element Msg)
executionInput formValues errors task =
    case task.validator of
        BooleanSchema _ ->
            [ Element.el [] (text "oh no") ]

        ObjectSchema subSchema ->
            schemaToForm formValues errors subSchema


boldKvPair : String -> String -> List (Element msg)
boldKvPair s1 s2 =
    [ Element.el
        [ Font.bold
        ]
        (text s1)
    , Element.el [] (text s2)
    ]


taskDetailColumn : List (Element msg) -> Element msg
taskDetailColumn =
    column
        [ height (fillPortion 2)
        , width fill
        , Element.alignTop
        , padding 10
        , spacing 10
        ]


granaryTaskDetailPairs : TaskDetail -> List (Element msg)
granaryTaskDetailPairs detail =
    [ row [ Font.bold ]
        [ Element.el
            [ Border.widthEach
                { bottom = 1
                , left = 0
                , right = 0
                , top = 0
                }
            ]
            (text "Model Details")
        ]
    , row [] <| boldKvPair "Name: " detail.task.name
    , row [] <| boldKvPair "Task ID: " (Uuid.toString detail.task.id)
    , row [] <| boldKvPair "Job Definition: " detail.task.jobDefinition
    , row [] <| boldKvPair "Job Queue: " detail.task.jobQueue
    ]


getErrField : Validation.Error -> String
getErrField err =
    case err.jsonPointer.path of
        [] ->
            "ROOT"

        errs ->
            List.intersperse "." errs
                |> String.concat


makeErr : Validation.Error -> List (Element Msg)
makeErr err =
    case err.details of
        Validation.Required fields ->
            fields
                |> List.map (\s -> row [] [ text "Missing field: ", Element.el [ fontRed ] (text s) ])

        Validation.AlwaysFail ->
            [ row [] [ text "Invalid json" ] ]

        Validation.InvalidType t ->
            [ text t ]

        Validation.RequiredProperty ->
            []

        _ ->
            [ row []
                [ text "I can't tell what else is wrong with "
                , err.jsonPointer.path
                    |> List.head
                    |> Maybe.withDefault "ROOT"
                    |> (Element.el [ fontRed ] << text)
                ]
            ]


getErrorElem : Result (List Validation.Error) JD.Value -> String -> Element Msg
getErrorElem result rawValue =
    case ( result, rawValue ) of
        ( _, "" ) ->
            row [] [ text "Enter JSON for this model's schema" ]

        -- should be a button, but _soon_
        ( Result.Ok _, _ ) ->
            row [] [ Element.el [] (submitButton ExecutionSubmit) ]

        ( Err errs, _ ) ->
            column [ spacing 3 ] (errs |> List.concatMap makeErr)


executionsPane : TaskDetail -> List (Element Msg)
executionsPane detail =
    if detail.addingExecution then
        [ row []
            [ Input.multiline
                []
                { onChange = ExecutionInput
                , text = detail.newExecutionRaw
                , placeholder = Input.placeholder [] (text "{}") |> Just
                , label = Input.labelAbove [] (text "Execution input")
                , spellcheck = True
                }
            ]
        , getErrorElem detail.newExecution detail.newExecutionRaw
        ]

    else
        [ row [ Font.bold ]
            [ text "Executions: "
            , newExecutionButton detail.task.id detail.task.validator
            ]
        , executionsTable detail
        ]


view : Model -> Browser.Document Msg
view model =
    case ( model.secrets, model.taskDetail ) of
        ( Just _, Just detail ) ->
            { title = detail.task.name
            , body =
                [ Element.layout [] <|
                    column [ width fill ]
                        [ logo [ width fill ] 100
                        , navBar [ homeCrumb, taskCrumb detail.task ]
                        , row [ height fill, width fill ]
                            [ taskDetailColumn <| granaryTaskDetailPairs detail
                            , taskDetailColumn <| executionsPane detail
                            ]
                        ]
                ]
            }

        ( Just _, Nothing ) ->
            { title = "Available Models"
            , body =
                [ Element.layout [] <|
                    column [ width fill ]
                        [ logo [ width fill ] 100
                        , navBar [ homeCrumb ]
                        , row []
                            [ column
                                [ fillPortion 1 |> width
                                , height fill
                                ]
                                (model.granaryTasks
                                    |> List.map
                                        (taskCard
                                            (Maybe.map .id model.selectedTask)
                                        )
                                )
                            , column
                                [ fillPortion 3 |> width
                                , height fill
                                ]
                                (Maybe.withDefault
                                    []
                                    (model.selectedTask |> Maybe.map (executionInput model.formValues model.taskValidationErrors))
                                )
                            ]
                        ]
                ]
            }

        ( Nothing, _ ) ->
            { title = "Granary Model Dashboard"
            , body =
                [ Element.layout [] <|
                    column [ spacing 3, Element.centerX, Element.centerY, width Element.shrink ]
                        [ row [ width fill ] [ logo [] 200 ]
                        , row [ width fill ]
                            [ textInput [] TokenInput model.secretsUnsubmitted "Enter a token" "Token input"
                            ]
                        , row [ width fill ] [ submitButton TokenSubmit ]
                        ]
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
