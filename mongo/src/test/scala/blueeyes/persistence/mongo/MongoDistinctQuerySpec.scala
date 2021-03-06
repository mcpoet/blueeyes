package blueeyes.persistence.mongo

import org.specs2.mutable.Specification
import dsl._
import MongoFilterOperators._
import blueeyes.json.JPath

class MongoDistinctQuerySpec extends Specification{
  "'where' method sets new filter" in {
    val query = distinct("foo").from("collection")
    
    query.where("name" === "Joe") mustEqual (MongoDistinctQuery(JPath("foo"), "collection", Some(MongoFieldFilter("name", $eq, "Joe"))))
  }
}
