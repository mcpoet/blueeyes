package blueeyes.persistence.mongo.json

import blueeyes.core.data.PartialBijection
import blueeyes.json._
import blueeyes.persistence.mongo.MongoBijection

import com.mongodb.{BasicDBObject, DBObject}
import org.bson.types.ObjectId
import org.apache.commons.codec.binary.Hex

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.ISODateTimeFormat

import scala.collection.JavaConverters._

import scalaz.{Validation, Success, NonEmptyList, ValidationNEL}
import scalaz.Validation._
import scalaz.NonEmptyList._
import scalaz.syntax.validation._
import scalaz.syntax.traverse._
import scalaz.std.AllInstances._

object MongoJValueBijection extends MongoBijection[JValue, JField, JObject] {
  final val ISO8601 = ISODateTimeFormat.basicDateTime

  override def extractString (value: String)                  = JString(value).success
  override def extractInteger(value: java.lang.Integer)       = JNum(value.intValue).success
  override def extractLong   (value: java.lang.Long)          = JNum(value.longValue).success
  override def extractFloat  (value: java.lang.Float)         = JNum(value.floatValue).success
  override def extractDouble (value: java.lang.Double)        = JNum(value.doubleValue).success
  override def extractBoolean(value: java.lang.Boolean)       = JBool(value).success
  override def extractDate   (value: java.util.Date)          = JString(ISO8601.print(new DateTime(value.getTime, DateTimeZone.UTC))).success
  override def extractRegex  (value: java.util.regex.Pattern) = "Regex type is not supported".failure.toValidationNEL
  override def extractBinary (value: Array[Byte])             = "Binary type is not supported".failure.toValidationNEL
  override def extractId     (value: ObjectId)                = JString("ObjectId(\"" + Hex.encodeHexString(value.toByteArray) + "\")").success
  override def buildNull                                      = JNull.success

  override def buildArray(value: Seq[JValue])                 = JArray(value.toList)
  override def buildField(name: String, value: JValue)        = JField(name, value)
  override def buildObject(fields: Seq[JField])               = JObject(fields.toList)

  def unapply(jobject: JObject): ValidationNEL[String, DBObject] = {
    jobject.fields.foldLeft(success[NonEmptyList[String], BasicDBObject](new BasicDBObject)) {
      case (success @ Success(obj), JField(name, JUndefined)) => success
      case (Success(obj), JField(name, value)) => toStorageValue(value).map(obj.append(name, _))
      case (failure, _) => failure
    }
  }

  private val ObjectIdPattern = """ObjectId\("([0-9a-f]*)"\)""".r

  private def toStorageValue(v: JValue): ValidationNEL[String, Any] = (v: @unchecked) match {
    case JNull | JUndefined => success(null)
    case JString(ObjectIdPattern(id)) => new ObjectId(Hex.decodeHex(id.toCharArray)).success
    case JString(x) => x.success
    case JNum(x)    => x.doubleValue.success
    case JBool(x)   => x.success
    case JArray(x)  => x.map(toStorageValue).sequence[({type N[B] = ValidationNEL[String, B]})#N, Any].map(_.asJava)
    case jobject @ JObject(_) => unapply(jobject)
  }
}

trait BijectionsMongoJson {
  implicit val MongoToJson: PartialBijection[DBObject, JObject] = MongoJValueBijection
  implicit val JsonToMongo: PartialBijection[JObject, DBObject] = MongoToJson.inverse
}

object BijectionsMongoJson extends BijectionsMongoJson

