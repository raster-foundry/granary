package com.rasterfoundry.granary.database

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.rasterfoundry.granary.datamodel.{PageRequest, Task}

import java.util.UUID

object TaskDao {
  val selectF = fr"select id, name, validator, job_definition, job_queue FROM tasks"

  def listTasks(pageRequest: PageRequest): ConnectionIO[List[Task]] = {
    Page(selectF, pageRequest).query[Task].to[List]
  }

  def getTask(id: UUID): ConnectionIO[Option[Task]] =
    (selectF ++ Fragments.whereOr(fr"id = ${id}")).query[Task].option

  def unsafeGetTask(id: UUID): ConnectionIO[Task] =
    (selectF ++ Fragments.whereOr(fr"id = ${id}")).query[Task].unique

  def insertTask(task: Task.Create): ConnectionIO[Task] = {
    val fragment = fr"""
      INSERT INTO tasks
        (id, name, validator, job_definition, job_queue)
      VALUES
        (uuid_generate_v4(), ${task.name}, ${task.validator}, ${task.jobDefinition}, ${task.jobQueue})
    """
    fragment.update.withUniqueGeneratedKeys[Task](
      "id",
      "name",
      "validator",
      "job_definition",
      "job_queue"
    )
  }

  def deleteTask(taskId: UUID): ConnectionIO[Option[Int]] =
    fr"DELETE FROM tasks WHERE id = $taskId".update.run map {
      case 0 => None
      case n => Some(n)
    }

}
