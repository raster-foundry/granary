module Pages.ExecutionList exposing (ExecutionListModel, emptyExecutionListModel, executionList)

import Element exposing (Element, column, fill, padding, row, spacing, text, width)
import Element.Font as Font
import Element.Input as Input
import Framework.Card as Card
import Maybe.Extra exposing (orElse)
import Set exposing (Set)
import Styled exposing (styledPrimaryText, textInput)
import Types exposing (GranaryExecution, Msg(..), StacAsset)
import Uuid as Uuid



-- Model


type alias ExecutionListModel =
    { selectedExecutions : Set String
    , executionNameSearch : Maybe String
    , executions : List GranaryExecution
    , forTask : Maybe Uuid.Uuid
    }


emptyExecutionListModel : ExecutionListModel
emptyExecutionListModel =
    { selectedExecutions = Set.empty
    , executionNameSearch = Nothing
    , executions = []
    , forTask = Nothing
    }



-- Helper functions


toEmoji : GranaryExecution -> String
toEmoji execution =
    case ( execution.statusReason, execution.results ) of
        ( Just _, _ ) ->
            "❌ Failed"

        ( _, _ :: _ ) ->
            "✅ Succeeded"

        _ ->
            "🏃\u{200D}♀️ Running"



-- View


executionAssets : Bool -> GranaryExecution -> List (Element Msg)
executionAssets showAssets execution =
    if List.isEmpty execution.results then
        []

    else
        row [ spacing 3 ]
            [ Input.button []
                { onPress = ToggleShowAssets execution.id |> Just
                , label = text "➕"
                }
            , styledPrimaryText [] " Show assets"
            ]
            :: (if showAssets then
                    executionAssetsList execution execution.results

                else
                    []
               )


executionAssetsList : GranaryExecution -> List StacAsset -> List (Element Msg)
executionAssetsList execution =
    let
        assetName asset =
            asset.title
                |> orElse asset.description
                |> Maybe.withDefault
                    (asset.roles |> List.intersperse ", " |> String.concat)
    in
    List.map
        (\asset ->
            Element.downloadAs []
                { url = asset.href
                , filename = execution.name
                , label =
                    styledPrimaryText [ Font.underline ]
                        (assetName asset)
                }
        )


nameSearchInput : Maybe String -> Element Msg
nameSearchInput currValue =
    textInput
        [ width fill ]
        SearchExecutionName
        currValue
        "Search"
        "Search"
        |> List.singleton
        |> row [ width fill ]


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
        ([ row [] [ styledPrimaryText [ Font.bold ] execution.name ]
         , row [] [ styledPrimaryText [] ("Status: " ++ toEmoji execution) ]
         ]
            ++ executionAssets showAssets execution
        )
    ]
        |> row [ width fill ]


executionList : ExecutionListModel -> Element Msg
executionList model =
    let
        showAssets execution =
            Set.member (Uuid.toString execution.id) model.selectedExecutions

        card execution =
            executionCard (showAssets execution) execution
    in
    nameSearchInput model.executionNameSearch
        :: (model.executions
                |> List.map card
           )
        |> column [ Element.centerX, spacing 10, padding 15 ]
