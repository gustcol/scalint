package com.example

import scala.concurrent.{ExecutionContext, Future}

/**
 * A well-written Scala class demonstrating best practices
 */
case class User(
  id: Long,
  name: String,
  email: String,
  age: Int
)

object UserService {

  def findById(id: Long)(implicit ec: ExecutionContext): Future[Option[User]] = {
    Future.successful(None)
  }

  def validateAge(age: Int): Either[String, Int] = {
    if (age >= 0 && age <= 150) Right(age)
    else Left("Invalid age")
  }

  def formatUser(user: User): String = {
    s"User: ${user.name} (${user.email})"
  }

  def processUsers(users: List[User]): List[String] = {
    users.collect {
      case user if user.age >= 18 => formatUser(user)
    }
  }

  def findAdults(users: List[User]): Boolean = {
    users.exists(_.age >= 18)
  }

  def countByAge(users: List[User]): Map[Int, Int] = {
    users.groupBy(_.age).view.mapValues(_.size).toMap
  }
}
