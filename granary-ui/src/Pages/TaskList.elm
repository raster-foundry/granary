module Pages.TaskList exposing
    ( FormValues
    , TaskListModel
    , decoderGranaryTask
    , emptyFormValues
    , emptyTaskListModel
    , setInputState
    , showType
    , taskList
    )

import Array
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
import Element.Font as Font
import Element.Input as Input
import FileInput as FileInput
import Framework.Button as Button
import Html.Attributes as HA
import Json.Decode as JD
import Json.Encode as JE
import Json.Schema as Schema
import Json.Schema.Definitions as Schema
    exposing
        ( Schema(..)
        , SingleType(..)
        , Type(..)
        )
import Json.Schema.Validation as Validation
import Styled exposing (secondaryShadow, styledPrimaryText, styledSecondaryText, submitButton, textInput)
import Types exposing (ExecutionCreate, ExecutionCreateError(..), GranaryTask, InputEvent(..), Msg(..))
import Urls exposing (executionsUrl)
import Uuid as Uuid



-- MODEL


type alias FormValues =
    { fromSchema : Dict String (Result String JD.Value)
    , executionName : Maybe String
    }


emptyFormValues : FormValues
emptyFormValues =
    { fromSchema = Dict.empty
    , executionName = Nothing
    }


type alias ExecutionCreate =
    { name : String
    , taskId : Uuid.Uuid
    , arguments : JD.Value
    }


type alias TaskListModel =
    { tasks : List GranaryTask
    , selectedTask : Maybe GranaryTask
    , formValues : FormValues
    , taskValidationErrors : Dict String ExecutionCreateError
    , activeSchema : Maybe Schema.Schema
    , inputState : Maybe InputEvent
    }


emptyTaskListModel : TaskListModel
emptyTaskListModel =
    { tasks = []
    , selectedTask = Nothing
    , formValues = emptyFormValues
    , taskValidationErrors = Dict.empty
    , activeSchema = Nothing
    , inputState = Nothing
    }


setInputState : InputEvent -> TaskListModel -> TaskListModel
setInputState event model =
    { model | inputState = Just event }


toExecutionCreate : String -> Uuid.Uuid -> FormValues -> ExecutionCreate
toExecutionCreate fallbackName taskId formValues =
    let
        goodFields =
            Dict.toList formValues.fromSchema
                |> List.filterMap
                    (\( k, v ) ->
                        Result.toMaybe v
                            |> Maybe.map (\goodVal -> ( k, goodVal ))
                    )

        executionName =
            formValues.executionName |> Maybe.withDefault fallbackName
    in
    ExecutionCreate executionName taskId (JE.object goodFields)


decoderGranaryTask : JD.Decoder GranaryTask
decoderGranaryTask =
    JD.map5
        GranaryTask
        (JD.field "id" Uuid.decoder)
        (JD.field "name" JD.string)
        (JD.field "validator" (JD.field "schema" Schema.decoder))
        (JD.field "jobDefinition" JD.string)
        (JD.field "jobQueue" JD.string)



-- Helper functions


isOk : Result e a -> Bool
isOk res =
    case res of
        Result.Ok _ ->
            True

        Result.Err _ ->
            False


toValue : Schema.SubSchema -> String -> Result String JD.Value
toValue schema s =
    encodeValue schema.type_ s


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


allowTaskSubmit : Maybe Schema -> FormValues -> Bool
allowTaskSubmit schema formValues =
    case ( formValues.executionName, schema ) of
        ( Nothing, _ ) ->
            False

        ( _, Nothing ) ->
            False

        ( Just s, Just schm ) ->
            not (String.isEmpty s)
                && List.foldl (\x y -> x && y) True (List.map isOk (Dict.values formValues.fromSchema))
                && (Dict.size formValues.fromSchema
                        == numProperties schm
                   )



-- VIEW


fontRed : Element.Attr d m
fontRed =
    rgb255 255 0 0 |> Font.color


getErrField : Validation.Error -> String
getErrField err =
    case err.jsonPointer.path of
        [] ->
            "ROOT"

        errs ->
            List.intersperse "." errs
                |> String.concat


taskCard : GranaryTask -> Element Msg
taskCard task =
    column (width fill :: spacing 5 :: Element.centerY :: Button.simple)
        [ row [ width fill, spacing 3 ]
            [ styledPrimaryText [ Element.alignLeft ] task.name
            , Input.button
                [ Element.alignRight, Element.htmlAttribute <| HA.style "margin" "0 12px" ]
                { label = text "➕"
                , onPress = NewExecutionForTask task |> Just
                }
            , tasksLink task
            ]
        ]


tasksLink : GranaryTask -> Element Msg
tasksLink task =
    Element.link []
        { url = executionsUrl Nothing (Just task.id)
        , label = styledSecondaryText [ Font.underline ] "Executions"
        }


makeHelpfulErrorMessage : ExecutionCreateError -> List (Element Msg)
makeHelpfulErrorMessage err =
    case err of
        SchemaError errs ->
            List.concatMap makeErr errs

        DecodingError _ ->
            [ row [ width (Element.minimum 300 fill) ] [ text "Unexpected JSON structure" ] ]


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


schemaToForm : Maybe InputEvent -> Dict String (Result String JD.Value) -> Dict String ExecutionCreateError -> Schema.SubSchema -> List (Element Msg)
schemaToForm inputEvent formValues errors schema =
    let
        errs ( k, _ ) =
            Dict.get k errors |> Maybe.map makeHelpfulErrorMessage |> Maybe.withDefault []

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
                    (k ++ ": " ++ showType v.type_)
                    k

            else
                let
                    extraStyle =
                        case inputEvent of
                            Just Over ->
                                [ secondaryShadow ]

                            Just Enter ->
                                []

                            Just Leave ->
                                []

                            Just Pick ->
                                []

                            Nothing ->
                                []
                in
                Dict.get "TASK_GRID" formValues
                    |> Maybe.andThen
                        (\result ->
                            case result of
                                Result.Ok data ->
                                    always (row [] [ text "Successful geojson upload!" ] |> Just) data

                                Result.Err _ ->
                                    Nothing
                        )
                    |> Maybe.withDefault
                        (row
                            (width (Element.minimum 300 fill) :: extraStyle)
                            [ Element.html
                                (FileInput.view
                                    { onEnter = GeoJsonInputMouseInteraction Enter
                                    , onLeave = GeoJsonInputMouseInteraction Leave
                                    , onPick = GeoJsonInputMouseInteraction Pick
                                    , onDrop = GotFiles
                                    , onOver = GeoJsonInputMouseInteraction Over
                                    }
                                )
                            ]
                        )

        inputRow ( k, propSchema ) =
            case propSchema of
                ObjectSchema subSchema ->
                    column [ width fill, spacing 5 ]
                        (toInput ( k, subSchema )
                            :: errs ( k, subSchema )
                        )

                BooleanSchema _ ->
                    row [ spacing 5 ] [ toInput ( k, Schema.blankSubSchema ) ]

        makeInput (Schema.Schemata definitions) =
            List.map inputRow definitions
    in
    schema.properties
        |> Maybe.map makeInput
        |> Maybe.withDefault []


executionInput : Maybe InputEvent -> FormValues -> Dict String ExecutionCreateError -> GranaryTask -> List (Element Msg)
executionInput inputEvent formValues errors task =
    case task.validator of
        BooleanSchema _ ->
            [ Element.el [] (text "oh no -- this task's schema decoded as a \"BooleanSchema\"") ]

        ObjectSchema subSchema ->
            textInput [ width (Element.minimum 300 fill) ]
                NameExecution
                formValues.executionName
                "Execution name"
                "New execution name"
                :: schemaToForm
                    inputEvent
                    formValues.fromSchema
                    errors
                    subSchema


taskList : TaskListModel -> Element Msg
taskList model =
    column [ width fill ]
        [ row [ width fill ] [ styledPrimaryText [ Font.bold, Font.size 24, Element.centerX ] "Tasks" ]
        , row [ width fill ]
            (column
                [ fillPortion 1 |> width
                , spacing 10
                , padding 10
                , Element.alignTop
                ]
                (model.tasks
                    |> List.map taskCard
                )
                :: (model.selectedTask
                        |> Maybe.map
                            (\selected ->
                                [ column
                                    [ fillPortion 3 |> width
                                    , height fill
                                    , width fill
                                    , spacing 10
                                    , padding 10
                                    ]
                                    (executionInput model.inputState model.formValues model.taskValidationErrors selected
                                        ++ [ row [ width (Element.maximum 300 fill) ]
                                                [ submitButton (allowTaskSubmit model.activeSchema)
                                                    model.formValues
                                                    (toExecutionCreate selected.name selected.id model.formValues |> CreateExecution)
                                                ]
                                           ]
                                    )
                                ]
                            )
                        |> Maybe.withDefault []
                   )
            )
        ]
