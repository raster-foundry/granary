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
    , executionNameSearch = Maybe.Nothing
    , executions = []
    , forTask = Nothing
    }



-- Helper functions


toEmoji : GranaryExecution -> String
toEmoji execution =
    case ( execution.statusReason, execution.results ) of
        ( Just _, _ ) ->
            "❌"

        ( _, _ :: _ ) ->
            "✅"

        _ ->
            "🏃\u{200D}♀️"



-- View


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


executionAssetsList : List StacAsset -> List (Element Msg)
executionAssetsList =
    List.map
        (\asset ->
            Element.link []
                { url = asset.href
                , label =
                    styledPrimaryText [ Font.underline ]
                        (asset.title
                            |> orElse asset.description
                            |> Maybe.withDefault
                                (asset.roles |> List.intersperse ", " |> String.concat)
                        )
                }
        )


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
