package com.rasterfoundry.granary.database.meta

import com.rasterfoundry.granary.datamodel.JobStatus

import doobie._
import doobie.postgres.implicits._

trait EnumMeta {

  implicit val jobStatusMeta: Meta[JobStatus] =
    pgEnumString("job_status", JobStatus.fromString, _.repr)
}
