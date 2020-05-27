package com.rasterfoundry.granary.database

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.rasterfoundry.granary.datamodel.{PageRequest, Task, Token}

import java.util.UUID

object TaskDao {
  val selectF = fr"select id, name, validator, job_definition, job_queue, owner FROM tasks"

  def listTasks(token: Token, pageRequest: PageRequest): ConnectionIO[List[Task]] = {
    Page(selectF ++ Fragments.whereAndOpt(tokenToFilter(token)), pageRequest).query[Task].to[List]
  }

  def getTask(token: Token, id: UUID): ConnectionIO[Option[Task]] =
    (selectF ++ Fragments.whereAndOpt(Some(fr"id = ${id}"), tokenToFilter(token)))
      .query[Task]
      .option

  def unsafeGetTask(token: Token, id: UUID): ConnectionIO[Task] =
    (selectF ++ Fragments.whereAndOpt(Some(fr"id = ${id}"), tokenToFilter(token)))
      .query[Task]
      .unique

  def insertTask(token: Token, task: Task.Create): ConnectionIO[Task] = {
    val owner    = tokenToUserId(token)
    val fragment = fr"""
      INSERT INTO tasks
        (id, name, validator, job_definition, job_queue, owner)
      VALUES
        (uuid_generate_v4(), ${task.name}, ${task.validator}, ${task.jobDefinition}, ${task.jobQueue}, $owner)
    """
    fragment.update.withUniqueGeneratedKeys[Task](
      "id",
      "name",
      "validator",
      "job_definition",
      "job_queue",
      "owner"
    )
  }

  def deleteTask(token: Token, taskId: UUID): ConnectionIO[Option[Int]] =
    (fr"DELETE FROM tasks" ++ Fragments.whereAndOpt(
      Some(fr"id = $taskId"),
      tokenToFilter(token)
    )).update.run map {
      case 0 => None
      case n => Some(n)
    }

}
