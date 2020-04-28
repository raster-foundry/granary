package com.rasterfoundry.granary.database

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.rasterfoundry.granary.datamodel.Model

import java.util.UUID
import com.rasterfoundry.granary.datamodel.PageRequest

object ModelDao {
  val selectF = fr"select id, name, validator, job_definition, job_queue FROM models"

  def listModels(pageRequest: PageRequest): ConnectionIO[List[Model]] = {
    println(s"Yeah definitely listing models, page is:")
    println(pageRequest)
    Page(selectF, pageRequest).query[Model].to[List]
  }

  def getModel(id: UUID): ConnectionIO[Option[Model]] =
    (selectF ++ Fragments.whereOr(fr"id = ${id}")).query[Model].option

  def unsafeGetModel(id: UUID): ConnectionIO[Model] =
    (selectF ++ Fragments.whereOr(fr"id = ${id}")).query[Model].unique

  def insertModel(model: Model.Create): ConnectionIO[Model] = {
    val fragment = fr"""
      INSERT INTO models
        (id, name, validator, job_definition, job_queue)
      VALUES
        (uuid_generate_v4(), ${model.name}, ${model.validator}, ${model.jobDefinition}, ${model.jobQueue})
    """
    fragment.update.withUniqueGeneratedKeys[Model](
      "id",
      "name",
      "validator",
      "job_definition",
      "job_queue"
    )
  }

  def deleteModel(modelId: UUID): ConnectionIO[Option[Int]] =
    fr"DELETE FROM models WHERE id = $modelId".update.run map {
      case 0 => None
      case n => Some(n)
    }

}
