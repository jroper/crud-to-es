package com.example.web.views

import views.html.helper.FieldConstructor

package object html {
  implicit def fieldConstructor: FieldConstructor = FieldConstructor(foundationFieldConstructor.apply)
}
