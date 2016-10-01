package net.scalax.fsn.mix.slickbase

import indicator.rw.utils.SlickQueryBindImpl
import net.scalax.fsn.slick.operation._
import io.circe.Json
import net.scalax.fsn.core.FColumn
import net.scalax.fsn.slick.atomic.SlickRetrieve
import net.scalax.fsn.slick.model._
import slick.basic.BasicProfile
import slick.dbio.{DBIO, NoStream}
import slick.jdbc.JdbcActionComponent
import slick.lifted.{Query, Rep}
import slick.relational.RelationalProfile

import scala.concurrent.ExecutionContext

case class ListQueryWrap(
  columns: List[FColumn],
  listQueryBind: SlickQueryBindImpl
)(implicit val ec: ExecutionContext) {

  lazy val withExtraCols = OutSelectConvert.extraSubCol(columns)

  lazy val queryWrap: JsonQuery = OutSelect.encode(withExtraCols, listQueryBind)

  def result
  (defaultOrders: List[ColumnOrder])
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream]
  ): JsonOut = {
    val gen = { slickParam: SlickParam =>
      queryWrap.jsonResult(defaultOrders).apply(slickParam).map { result =>
        result._1.map(InJsonConvert.writeJ) -> result._2
      }
    }
    JsonOut(withExtraCols.map(InPropertiesConvert.convertProperty), gen)
  }

  def result
  (orderColumn: String, isDesc: Boolean = true)
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream]
  ): JsonOut = {
    result(List(ColumnOrder(orderColumn, isDesc)))
  }

  def result
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream]
  ): JsonOut = {
    result(Nil)
  }

}

case class QueryWrap(
  binds: List[(RelationalProfile#Table[_], SlickQueryBindImpl)],
  listQueryWrap: ListQueryWrap
)(implicit val ec: ExecutionContext) {

  lazy val queryWrap: listQueryWrap.queryWrap.type = listQueryWrap.queryWrap
  val columns = listQueryWrap.columns
  lazy val properties = InPropertiesConvert.convertColumn(columns)

  def result
  (defaultOrders: List[ColumnOrder])
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
    retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
    insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
    deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
    updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
    ec: ExecutionContext
  ): QueryJsonInfo = {
    QueryJsonInfo(
      properties = properties,
      jsonGen = {
        listQueryWrap.result(defaultOrders)
      },
      retrieveGen = { v: Map[String, Json] =>
        /*val jsonData = indicator.rw.utils.rw2.InJsonConvert.readJPrimary(columns)(v)
        for {
          execInfo <- RetrieveWrapDeal.parseInsert(deleteWrap, jsonData)
          staticMany = indicator.rw.utils.rw2.InStaticManyConvert.convertList2Query(execInfo.fColumns)
          staticM <- DBIO.from(staticMany)
        } yield {
          val jsonResult = indicator.rw.utils.rw2.InJsonConvert.writeJ(execInfo.fColumns)
          StaticManyInfo(properties, jsonResult, staticM)
        }*/
        val jsonData = InJsonConvert.readJPrimary(columns)(v)
        for {
          execInfo <- RetrieveWrapDeal2.parseInsert(binds, jsonData)
          staticMany = InStaticManyConvert.convertList2Query(execInfo.columns)
          staticM <- DBIO.from(staticMany)
        } yield {
          val jsonResult = InJsonConvert.writeJ(execInfo.columns)
          StaticManyInfo(properties, jsonResult, staticM)
        }
      },
      insertGen = { v: Map[String, Json] =>
        val jsonData = InJsonConvert.readJNotInc(columns)(v)
        for {
          execInfo <- InsertWrapDeal2.parseInsert(binds, jsonData)
          staticMany = InStaticManyConvert.convertList2Query(execInfo.columns)
          staticM <- DBIO.from(staticMany)
        } yield {
          UpdateStaticManyInfo(execInfo.effectRows, staticM)
        }
      },
      deleteGen = (v: Map[String, Json]) => {
        val primaryColumns = columns.filter { col => FColumn.findOpt(col) { case retrieve: SlickRetrieve[col.DataType] => retrieve }.map(_.primaryGen.isDefined).getOrElse(false) }
        val jsonData = InJsonConvert.readJ(primaryColumns)(v)
        val staticMany = InStaticManyConvert.convertList2Query(jsonData)
        for {
          updateInfo <- DeleteWrapDeal2.parseInsert(binds, jsonData)
          staticM <- DBIO.from(staticMany)
        } yield {
          updateInfo.copy(many = staticM).effectRows
        }
      },
      updateGen = (v: Map[String, Json]) => {
        val jsonData = InJsonConvert.readJ(columns)(v)
        val staticMany = InStaticManyConvert.convertList2Query(jsonData)
        for {
          updateInfo <- UpdateWrapDeal2.parseInsert(binds, jsonData)
          staticM <- DBIO.from(staticMany)
        } yield {
          updateInfo.copy(many = staticM)
        }
      },
      staticMany = InStaticManyConvert.convertList2Ubw(columns)
    )
  }

  def result
  (orderColumn: String, isDesc: Boolean = true)
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
    retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
    insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
    deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
    updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
    ec: ExecutionContext
  ): QueryJsonInfo = {
    result(List(ColumnOrder(orderColumn, isDesc)))
  }

  def result
  (
    implicit
    jsonEv: Query[_, Seq[Any], Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[Seq[Any]], Seq[Any]],
    repToDBIO: Rep[Int] => BasicProfile#QueryActionExtensionMethods[Int, NoStream],
    retrieve: Query[_, String, Seq] => BasicProfile#StreamingQueryActionExtensionMethods[Seq[String], String],
    insertConv: Query[_, Seq[Any], Seq] => JdbcActionComponent#InsertActionExtensionMethods[Seq[Any]],
    deleteConV: Query[RelationalProfile#Table[_], _, Seq] => JdbcActionComponent#DeleteActionExtensionMethods,
    updateConV: Query[_, Seq[Any], Seq] => JdbcActionComponent#UpdateActionExtensionMethods[Seq[Any]],
    ec: ExecutionContext
  ): QueryJsonInfo = {
    result(Nil)
  }

}