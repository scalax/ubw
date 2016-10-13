package net.scalax.fsn.json.operation

import io.circe.Json
import io.circe.syntax._
import net.scalax.fsn.common.atomic.FProperty
import net.scalax.fsn.core.{FColumn, FsnColumn}
import net.scalax.fsn.json.atomic.{JsonReader, JsonWriter}

object JsonOperation {

  def read(eachColumn: FColumn): Map[String, Json] => FColumn = { data: Map[String, Json] =>
    val jsonReader = FColumn.find(eachColumn)({ case s: JsonReader[eachColumn.DataType] => s })
    val property = FColumn.find(eachColumn)({ case s: FProperty[eachColumn.DataType] => s })
    //lazy val defaultValue = FColumn.findOpt(eachColumn)({ case s: DefaultValue[eachColumn.DataType] => s }).map(_.value)

    /*def getDefaultOrThrow[S <: Exception](e: S): eachColumn.DataType = {
      defaultValue match {
        case Some(s) => s
        case _ => throw e
      }
    }*/

    lazy val fillData = CommonOperation.tryToFillIfEmpty(eachColumn)

    val eachColumnData: FColumn = data.get(property.proName) match {
      case Some(json) =>
        json.as[jsonReader.JsonType](jsonReader.reader).toOption match {
          case Some(data) =>
            FsnColumn(eachColumn.cols, Option(jsonReader.convert(data)))
          case _ =>
            if (fillData.data.isEmpty) {
              throw new Exception(s"字段 ${ property.proName } 的值不能被正确转换")
            }
            fillData
        }
      case None =>
        if (fillData.data.isEmpty) {
          throw new Exception(s"字段 ${ property.proName } 未被定义")
        }
        fillData
    }
    eachColumnData
    //FsnColumn(eachColumn.cols, Option(eachColumnData))
  }

  def readJ(columns: List[FColumn]): Map[String, Json] => List[FColumn] = { data: Map[String, Json] =>
    columns.map { eachColumn =>
      read(eachColumn)(data)
    }
  }

  /*def readJNotInc(columns: List[FColumn]): Map[String, Json] => List[FColumn] = { data: Map[String, Json] =>
    columns.map { eachColumn =>
      val isAutoInc = FColumn.findOpt(eachColumn) { case s: AutoInc[eachColumn.DataType] => s }.map(_.isAutoInc).getOrElse(false)
      if (isAutoInc) {
        eachColumn
      } else {
        read(eachColumn)(data)
      }
    }
  }

  def readJPrimary(columns: List[FColumn]): Map[String, Json] => List[FColumn] = { data: Map[String, Json] =>
    columns.map { eachColumn =>
      val isPrimary = FColumn.findOpt(eachColumn) { case s: SlickRetrieve[eachColumn.DataType] => s }.map(_.primaryGen.isDefined).getOrElse(false)
      if (isPrimary) {
        read(eachColumn)(data)
      } else {
        eachColumn
      }
    }
  }*/

  def readWithFilter(columns: List[FColumn])(filter: FColumn => Boolean): Map[String, Json] => List[FColumn] = { data: Map[String, Json] =>
    columns.map { eachColumn =>
      val isNeed = filter(eachColumn)
      if (isNeed) {
        read(eachColumn)(data)
      } else {
        eachColumn
      }
    }
  }

  def write(eachColumn: FColumn): (String, Json) = {
    val jsonWriter = FColumn.find(eachColumn)({ case s: JsonWriter[eachColumn.DataType] => s })
    val property = FColumn.find(eachColumn)({ case s: FProperty[eachColumn.DataType] => s })
    //lazy val defaultValue = FColumn.findOpt(eachColumn)({ case s: DefaultValue[eachColumn.DataType] => s }).map(_.value)

    /*def getDefaultOrThrow[S <: Exception](e: S): eachColumn.DataType = {
      defaultValue match {
        case Some(s) => s
        case _ => throw e
      }
    }*/
    val colData = CommonOperation.genDataFromFColumn(eachColumn)
    val eachColumnData: eachColumn.DataType = eachColumn.data match {
      case Some(data) =>
        data
      case None =>
        throw new Exception(s"字段 ${ property.proName } 未被定义")
    }

    property.proName -> jsonWriter.convert(eachColumnData).asJson(jsonWriter.writer)
  }

  def writeJ(columns: List[FColumn]): Map[String, Json] = {
    columns.map { eachColumn =>
      write(eachColumn)
    }.toMap
  }

}