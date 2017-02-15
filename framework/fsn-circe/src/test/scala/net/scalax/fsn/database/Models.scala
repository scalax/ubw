package net.scalax.fsn.database.test

import slick.jdbc.H2Profile.api._
/**
 * Created by djx314 on 15-6-22.
 */

case class Friend(
  id: Option[Long],
  name: String,
  nick: String
)

class FriendTable(tag: Tag) extends Table[Friend](tag, "firend") /*with FsnTable*/ {
  def id = column[Long]("id", O.AutoInc)
  def name = column[String]("name")
  def nick = column[String]("nick")

  /*def fid = fsnColumn[Long]("id", O.AutoInc)
  def fname = fsnColumn[String]("name")
  def fnick = fsnColumn[String]("nick")*/

  def * = (id.?, name, nick).mapTo[Friend]
}