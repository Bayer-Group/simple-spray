package demo

import spray.json.DefaultJsonProtocol

case class Stuff(id: Int, data: String)

object Stuff extends DefaultJsonProtocol {
    implicit val stuffFormat = jsonFormat2(Stuff.apply)
}

