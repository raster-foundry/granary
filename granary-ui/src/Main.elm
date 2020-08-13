module Main exposing (main)

import Array
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
import Result
import Time
import Url
import Url.Parser as Parser exposing ((</>), (<?>))
import Url.Parser.Query as Query
import Uuid as Uuid


type alias PaginatedResponse a =
    { page : Int
    , limit : Int
    , results : List a
    }


type alias GranaryToken =
    String


type alias Model =
    { url : Url.Url
    , key : Nav.Key
    , route : Route
    , granaryTasks : List GranaryTask
    , granaryExecutions : List GranaryExecution
    , activeSchema : Maybe Schema
    , taskValidationErrors : Dict String (List Validation.Error)
    , formValues : Dict String (Result String JD.Value)
    , selectedTask : Maybe GranaryTask
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


type Route
    = Login
    | TaskList (Maybe GranaryToken)
    | ExecutionList (Maybe Uuid.Uuid) (Maybe GranaryToken)
    | ExecutionDetail Uuid.Uuid (Maybe GranaryToken)


routeParser : Parser.Parser (Route -> a) a
routeParser =
    let
        uuidQueryParam strings =
            List.head strings |> Maybe.andThen Uuid.fromString
    in
    Parser.oneOf
        [ Parser.map ExecutionList
            (Parser.s "executions" <?> Query.custom "taskId" uuidQueryParam <?> Query.string "token")
        , Parser.map ExecutionDetail (Parser.s "executions" </> Parser.custom "UUID" Uuid.fromString <?> Query.string "token")
        , Parser.map TaskList (Parser.s "tasks" <?> Query.string "token")
        ]


init : () -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init _ url key =
    ( { url = url
      , key = key
      , route = Login
      , granaryTasks = []
      , granaryExecutions = []
      , selectedTask = Nothing
      , activeSchema = Nothing
      , taskValidationErrors = Dict.empty
      , formValues = Dict.empty
      , secrets = Nothing
      , secretsUnsubmitted = Nothing
      }
    , Nav.pushUrl key (Url.toString url)
    )



---- UPDATE ----


toExecutionCreate : Uuid.Uuid -> Dict String (Result String JD.Value) -> ExecutionCreate
toExecutionCreate taskId validatedFields =
    let
        goodFields =
            Dict.toList validatedFields
                |> List.filterMap
                    (\( k, v ) ->
                        Result.toMaybe v
                            |> Maybe.map (\goodVal -> ( k, goodVal ))
                    )
    in
    ExecutionCreate taskId (JE.object goodFields)


executionsUrl : Maybe Uuid.Uuid -> String
executionsUrl =
    (++) "/api/executions"
        << (Maybe.withDefault ""
                << Maybe.map ((++) "?taskId=" << Uuid.toString)
           )


fetchTasks : GranaryToken -> Cmd.Cmd Msg
fetchTasks token =
    B.get "/api/tasks"
        |> B.withExpect (Http.expectJson GotTasks (paginatedDecoder decoderGranaryModel))
        |> B.withBearerToken token
        |> B.request


fetchExecutions : Maybe Uuid.Uuid -> GranaryToken -> Cmd.Cmd Msg
fetchExecutions taskId token =
    executionsUrl taskId
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


type Msg
    = GotTasks (Result Http.Error (PaginatedResponse GranaryTask))
    | GotExecutions (Maybe Uuid.Uuid) (Result Http.Error (PaginatedResponse GranaryExecution))
    | Navigation Browser.UrlRequest
    | UrlChanged Url.Url
    | TokenInput String
    | TokenSubmit
    | CreatedExecution (Result Http.Error GranaryExecution)
    | TaskSelect GranaryTask
    | ValidateWith
        { schema : Schema.SubSchema
        , fieldName : String
        , fieldValue : Result String JD.Value
        }
    | CreateExecution ExecutionCreate


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        UrlChanged url ->
            let
                chooseToken urlToken modelToken =
                    case ( urlToken, modelToken ) of
                        ( Just t, _ ) ->
                            Just t

                        ( _, Just t ) ->
                            Just t

                        _ ->
                            Nothing

                routeResult =
                    Parser.parse routeParser url

                getCmd f t =
                    let
                        maybeCmd =
                            t |> Maybe.map f
                    in
                    case maybeCmd of
                        Just c ->
                            ( c, t )

                        _ ->
                            ( Cmd.none, t )

                ( cmd, token ) =
                    case ( routeResult, model.secrets ) of
                        ( Just (TaskList urlToken), modelToken ) ->
                            chooseToken urlToken modelToken
                                |> getCmd fetchTasks

                        ( Just (ExecutionList taskId urlToken), modelToken ) ->
                            chooseToken urlToken modelToken
                                |> getCmd (fetchExecutions taskId)

                        ( _, Just t ) ->
                            ( Nav.pushUrl model.key ("/tasks?token=" ++ t), Just t )

                        ( _, Nothing ) ->
                            ( Cmd.none, Nothing )
            in
            ( { model | secrets = token }, cmd )

        Navigation urlRequest ->
            case urlRequest of
                Browser.Internal url ->
                    ( model, Nav.pushUrl model.key (Url.toString url) )

                Browser.External href ->
                    ( model, Nav.load href )

        TaskSelect task ->
            ( { model
                | selectedTask = Just task
                , taskValidationErrors = Dict.empty
                , formValues = Dict.empty
                , activeSchema = Just task.validator
              }
            , Cmd.none
            )

        GotTasks (Ok tasks) ->
            ( { model | granaryTasks = tasks.results, route = TaskList model.secrets, secretsUnsubmitted = Nothing }, Cmd.none )

        GotTasks (Err _) ->
            ( model, Cmd.none )

        GotExecutions taskId (Ok executionsPage) ->
            ( { model | route = ExecutionList taskId model.secrets, granaryExecutions = executionsPage.results }, Cmd.none )

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
                    ++ (model.selectedTask
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

        CreateExecution executionCreate ->
            ( model, maybePostExecution model.secrets executionCreate )



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


secondaryShadow : Element.Attr deco msg
secondaryShadow =
    Border.shadow
        { offset = ( 0.1, 0.1 )
        , size = 2
        , blur = 3
        , color = secondary
        }


styledPrimaryText : List (Element.Attribute msg) -> String -> Element msg
styledPrimaryText attrs s =
    el (Font.color primary :: attrs) (text s)


styledSecondaryText : List (Element.Attribute msg) -> String -> Element msg
styledSecondaryText attrs s =
    el (Font.color secondary :: attrs) (text s)


submitButton : (a -> Bool) -> a -> String -> Msg -> Element Msg
submitButton predicate value hint msg =
    let
        allowSubmit =
            predicate value
    in
    Element.el
        []
        (Input.button
            (Button.simple
                ++ [ Background.color accent
                   , Element.centerX
                   , Border.color primary
                   ]
            )
            { onPress =
                if allowSubmit then
                    Just msg

                else
                    Nothing
            , label = styledPrimaryText [] "Submit"
            }
        )
        :: (if allowSubmit then
                []

            else
                [ styledSecondaryText [] hint
                ]
           )
        |> column [ spacing 5 ]


textInput : List (Element.Attribute msg) -> (String -> msg) -> Maybe String -> String -> String -> Element msg
textInput attrs f maybeText placeholder label =
    Input.text
        (Element.focused
            [ secondaryShadow ]
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
                    [ secondaryShadow ]

                else
                    []
               )
        )
        [ Input.button
            Button.simple
            { label = text task.name
            , onPress = TaskSelect task |> Just
            }
        ]


isOk : Result e a -> Bool
isOk res =
    case res of
        Result.Ok _ ->
            True

        Result.Err _ ->
            False


toEmoji : GranaryExecution -> String
toEmoji execution =
    case ( execution.statusReason, execution.results ) of
        ( Just _, _ ) ->
            "😱"

        ( _, _ :: _ ) ->
            "✨"

        _ ->
            "🏃\u{200D}♀️"


numProperties : Schema -> Int
numProperties schema =
    let
        schemataLength (Schema.Schemata props) =
            List.length props
    in
    case schema of
        ObjectSchema subSchema ->
            subSchema.properties |> Maybe.map schemataLength |> Maybe.withDefault 0

        BooleanSchema _ ->
            0


allowModelSubmit : Maybe Schema -> Dict String (Result String JD.Value) -> Bool
allowModelSubmit schema enteredValues =
    case schema of
        Just schm ->
            List.foldl (\x y -> x && y) True (List.map isOk (Dict.values enteredValues))
                && (Dict.size enteredValues
                        == numProperties schm
                   )

        Nothing ->
            False


toResult : String -> Maybe JD.Value -> Result String JD.Value
toResult s m =
    case m of
        Just v ->
            Result.Ok v

        Nothing ->
            Result.Err s


showType : Schema.Type -> String
showType t =
    case t of
        SingleType IntegerType ->
            "int"

        SingleType NumberType ->
            "float"

        SingleType StringType ->
            "string"

        SingleType BooleanType ->
            "boolean"

        SingleType ArrayType ->
            "array"

        SingleType ObjectType ->
            "object"

        SingleType NullType ->
            "null"

        AnyType ->
            "any"

        NullableType st ->
            "Maybe " ++ showType (SingleType st)

        UnionType sts ->
            List.map (showType << SingleType) sts
                |> List.intersperse ", "
                |> List.foldl (++) ""
                |> (++) "one of "


encodeValue : Schema.Type -> String -> Result String JD.Value
encodeValue t s =
    let
        defaulter =
            toResult s
    in
    case t of
        SingleType IntegerType ->
            String.toInt s |> Maybe.map JE.int |> defaulter

        SingleType NumberType ->
            String.toFloat s |> Maybe.map JE.float |> defaulter

        SingleType StringType ->
            Just (JE.string s) |> defaulter

        SingleType BooleanType ->
            (case s of
                "true" ->
                    Just (JE.bool True)

                "false" ->
                    Just (JE.bool False)

                _ ->
                    Nothing
            )
                |> defaulter

        SingleType ArrayType ->
            Just (JE.array JE.string (String.split "," s |> Array.fromList)) |> defaulter

        SingleType ObjectType ->
            (case JD.decodeString (JD.dict JD.value) s of
                Result.Ok v ->
                    Just (JE.dict identity identity v)

                _ ->
                    Nothing
            )
                |> defaulter

        SingleType NullType ->
            Just JE.null |> defaulter

        AnyType ->
            Just (JE.string s) |> defaulter

        NullableType st ->
            case s of
                "null" ->
                    Just JE.null
                        |> defaulter

                _ ->
                    encodeValue (SingleType st) s

        UnionType sts ->
            List.map (\st -> encodeValue (SingleType st) s) sts
                |> List.filter isOk
                |> List.head
                |> Maybe.withDefault (Result.Err s)


toValue : Schema.SubSchema -> String -> Result String JD.Value
toValue schema s =
    encodeValue schema.type_ s


schemaToForm : Dict String (Result String JD.Value) -> Dict String (List Validation.Error) -> Schema.SubSchema -> List (Element Msg)
schemaToForm formValues errors schema =
    let
        errs ( k, _ ) =
            Dict.get k errors |> Maybe.withDefault [] |> List.concatMap makeErr

        toInput ( k, v ) =
            if String.toLower k /= "task_grid" then
                textInput [ width (Element.minimum 300 fill) ]
                    (\s ->
                        ValidateWith
                            { schema = v
                            , fieldName = k
                            , fieldValue = toValue v s
                            }
                    )
                    (case Dict.get k formValues of
                        Just (Result.Ok jsonValue) ->
                            Just
                                (JE.encode 0 jsonValue
                                    |> String.toList
                                    |> List.filter ((/=) '"')
                                    |> String.fromList
                                )

                        Just (Result.Err s) ->
                            Just s

                        _ ->
                            Nothing
                    )
                    k
                    k

            else
                row [] [ text "file input goes here" ]

        inputRow ( k, propSchema ) =
            case propSchema of
                ObjectSchema subSchema ->
                    column [ spacing 10 ]
                        [ row [] [ toInput ( k, subSchema ) ]
                        , row [] (errs ( k, subSchema ))
                        ]

                BooleanSchema _ ->
                    row [ spacing 5 ] [ toInput ( k, Schema.blankSubSchema ) ]

        makeInput (Schema.Schemata definitions) =
            List.map inputRow definitions
    in
    schema.properties
        |> Maybe.map makeInput
        |> Maybe.withDefault []


executionInput : Dict String (Result String JD.Value) -> Dict String (List Validation.Error) -> GranaryTask -> List (Element Msg)
executionInput formValues errors task =
    case task.validator of
        BooleanSchema _ ->
            [ Element.el [] (text "oh no") ]

        ObjectSchema subSchema ->
            schemaToForm formValues errors subSchema


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
            [ ("I can't tell what else is wrong with " ++ getErrField err)
                |> text
            ]


secretPage : Browser.Document Msg
secretPage =
    { title = "You found a secret!"
    , body =
        [ Element.layout [] <|
            column [ spacing 3, Element.centerX, Element.centerY, width Element.shrink ]
                [ row [ width fill ] [ logo [] 200 ]
                , row [ width fill ] [ text "This page hasn't been implemented yet!" ]
                ]
        ]
    }


logoTop : List (Element Msg) -> Element Msg
logoTop rest =
    column [ width fill ] <|
        logo [ width fill ] 100
            :: rest


view : Model -> Browser.Document Msg
view model =
    case ( model.route, model.secrets ) of
        ( TaskList _, Just _ ) ->
            { title = "Available Models"
            , body =
                [ Element.layout [] <|
                    logoTop
                        [ row [ Element.centerX ]
                            [ column
                                [ fillPortion 1 |> width
                                , spacing 10
                                , padding 10
                                , Element.alignTop
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
                                , spacing 10
                                , padding 10
                                ]
                                (Maybe.withDefault
                                    [ styledPrimaryText [] "👈 Choose a model on the left" ]
                                    (model.selectedTask
                                        |> Maybe.map
                                            (\selected ->
                                                executionInput model.formValues model.taskValidationErrors selected
                                                    ++ [ row []
                                                            [ submitButton (allowModelSubmit model.activeSchema)
                                                                model.formValues
                                                                "Some inputs are invalid"
                                                                (toExecutionCreate selected.id model.formValues |> CreateExecution)
                                                            ]
                                                       ]
                                            )
                                    )
                                )
                            ]
                        ]
                ]
            }

        ( ExecutionList _ _, Just _ ) ->
            { title = "Execution list"
            , body =
                [ Element.layout [] <|
                    logoTop
                        (model.granaryExecutions
                            |> List.map
                                (\execution ->
                                    Element.el Card.simple (styledPrimaryText [] (Uuid.toString execution.id ++ toEmoji execution))
                                )
                        )
                ]
            }

        ( ExecutionDetail _ _, Just _ ) ->
            secretPage

        _ ->
            { title = "Granary Model Dashboard"
            , body =
                [ Element.layout [] <|
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
