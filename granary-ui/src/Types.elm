module Types exposing
    ( ExecutionCreate
    , GranaryExecution
    , GranaryTask
    , GranaryToken
    , Msg(..)
    , PaginatedResponse
    , StacAsset
    )

import Browser
import Http as Http
import Json.Decode as JD
import Json.Schema.Definitions as Schema
    exposing
        ( Schema(..)
        , SingleType(..)
        , Type(..)
        )
import Time
import Url
import Uuid as Uuid


type alias GranaryToken =
    String


type alias PaginatedResponse a =
    { page : Int
    , limit : Int
    , results : List a
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
    , name : String
    }


type alias ExecutionCreate =
    { name : String
    , taskId : Uuid.Uuid
    , arguments : JD.Value
    }


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
    | NameExecution String
    | CreateExecution ExecutionCreate
    | ToggleShowAssets Uuid.Uuid
    | SearchExecutionName String
    | AddTokenParam GranaryToken
    | GoHome
