module Urls exposing (apiExecutionsUrl, executionsUrl)

import Uuid as Uuid


apiExecutionsUrl : Maybe String -> Maybe Uuid.Uuid -> String
apiExecutionsUrl namesLike taskId =
    "/api" ++ executionsUrl namesLike taskId


executionsUrl : Maybe String -> Maybe Uuid.Uuid -> String
executionsUrl namesLike taskId =
    let
        baseUrl =
            "/executions"

        taskSearch =
            taskId
                |> Maybe.map ((++) "taskId=" << Uuid.toString)

        nameSearch =
            namesLike
                |> Maybe.map ((++) "name=")

        qp =
            [ taskSearch, nameSearch ]
                |> List.filterMap identity
                |> List.intersperse "&"
                |> String.concat
    in
    if String.isEmpty qp then
        baseUrl

    else
        baseUrl ++ "?" ++ qp
