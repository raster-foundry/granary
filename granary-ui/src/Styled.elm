module Styled exposing
    ( accent
    , primary
    , secondary
    , secondaryShadow
    , styledPrimaryText
    , styledSecondaryText
    , submitButton
    , textInput
    )

import Element as Element exposing (column, el, rgb255, spacing, text)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Element.Input as Input
import Framework.Button as Button
import Framework.Color
import Html.Attributes exposing (disabled, style)


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


styledPrimaryText : List (Element.Attribute msg) -> String -> Element.Element msg
styledPrimaryText attrs s =
    el (Font.color primary :: attrs) (text s)


styledSecondaryText : List (Element.Attribute msg) -> String -> Element.Element msg
styledSecondaryText attrs s =
    el (Font.color secondary :: attrs) (text s)


submitButton : (a -> Bool) -> a -> msg -> Element.Element msg
submitButton predicate value msg =
    let
        allowSubmit =
            predicate value

        bgColor =
            if allowSubmit then
                accent

            else
                Framework.Color.lightGrey
    in
    Element.el
        []
        (Input.button
            (Button.simple
                ++ [ Background.color bgColor
                   , Element.centerX
                   , Border.color primary
                   , not allowSubmit |> disabled |> Element.htmlAttribute
                   , (if allowSubmit then
                        "pointer"

                      else
                        "not-allowed"
                     )
                        |> style "cursor"
                        |> Element.htmlAttribute
                   ]
            )
            { onPress =
                if allowSubmit then
                    Just msg

                else
                    Nothing
            , label =
                Element.el
                    [ if allowSubmit then
                        Font.color primary

                      else
                        Font.color <| rgb255 168 168 168
                    ]
                <|
                    text
                        "Submit"
            }
        )
        |> List.singleton
        |> column [ spacing 5 ]


textInput : List (Element.Attribute msg) -> (String -> msg) -> Maybe String -> String -> String -> Element.Element msg
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
