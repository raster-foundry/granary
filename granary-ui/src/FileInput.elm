module FileInput exposing (InputEventHandler, view)

import File exposing (File)
import Html exposing (Attribute, Html, button, div, text)
import Html.Attributes exposing (style)
import Html.Events exposing (onClick, preventDefaultOn)
import Json.Decode as D


type alias InputEventHandler msg =
    { onEnter : msg
    , onOver : msg
    , onLeave : msg
    , onDrop : File -> List File -> msg
    , onPick : msg
    }


hijackOn : String -> D.Decoder msg -> Attribute msg
hijackOn event decoder =
    preventDefaultOn event (D.map hijack decoder)


hijack : msg -> ( msg, Bool )
hijack msg =
    ( msg, True )


dropDecoder : (File -> List File -> msg) -> D.Decoder msg
dropDecoder f =
    D.at [ "dataTransfer", "files" ] (D.oneOrMore f File.decoder)


view : InputEventHandler msg -> Html msg
view handler =
    div
        [ style "border-radius" "20px"
        , style "padding" "5px"
        , style "display" "flex"
        , style "flex-direction" "column"
        , style "justify-content" "center"
        , style "align-items" "center"
        , hijackOn "dragenter" (D.succeed handler.onEnter)
        , hijackOn "dragover" (D.succeed handler.onOver)
        , hijackOn "dragleave" (D.succeed handler.onLeave)
        , hijackOn "drop" (dropDecoder handler.onDrop)
        ]
        [ button [ onClick handler.onPick ] [ text "Select or drag and drop a task grid geojson" ]
        ]
