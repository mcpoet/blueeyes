// These tests were auto-generated by Lift Json XSchema - do not edit
package blueeyes.demo {
  import _root_.org.specs.Specification

  import blueeyes.json.JsonParser._
  import blueeyes.json.JsonAST._
  
  import blueeyes.json.xschema.DefaultSerialization._
  
  import blueeyes.demo.Serialization._
  import blueeyes.demo.Constants._

  object ExampleProductData {
    lazy val ExampleContact: blueeyes.demo.Contact = JObject(Nil).deserialize[blueeyes.demo.Contact]
  }

  object DataProductSerializationExamples extends Specification {
    "Deserialization of Contact succeeds even when information is missing" in {
      ExampleProductData.ExampleContact.isInstanceOf[blueeyes.demo.Contact] must be (true)
    }

    "Serialization of Contact has non-zero information content" in {
      ExampleProductData.ExampleContact.serialize mustNot be (JObject(Nil))
    }
  }
}