package spray.json

trait JsObjectBuildingImplicits {
  implicit def enrichTuple2[F: FieldName, T: JsonWriter](tuple: (F, T)): AddField =
    new AddField {
      def ~[F: FieldName, U: JsonWriter](next: (F, U)): JsObject =
        JsObject(FieldName.get(tuple._1) -> tuple._2.toJson, FieldName.get(next._1) -> next._2.toJson)
    }

  implicit def enrichTuple2(jsObj: JsObject): AddField =
    new AddField {
      def ~[F: FieldName, U: JsonWriter](next: (F, U)): JsObject =
        JsObject(jsObj.fields + ((FieldName.get(next._1), next._2.toJson)))
    }
}

trait AddField {
  def ~[F: FieldName, U: JsonWriter](next: (F, U)): JsObject
}
