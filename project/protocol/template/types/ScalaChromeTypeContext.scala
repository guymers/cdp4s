package protocol.template
package types

import protocol.util.StringUtils

final case class ScalaChromeTypeContext(
  domain: String,
  enumPackage: String,
  objPackage: String,
  referencePackage: String,
)

object ScalaChromeTypeContext {
  import StringUtils.escapeScalaVariable

  val modelPackage = "cdp4s.domain.model"
  val eventPackage = "cdp4s.domain.event"

  def defaultCtx(domain: String): ScalaChromeTypeContext = {
    val safeDomainName = escapeScalaVariable(domain)
    ScalaChromeTypeContext(
      domain = domain,
      enumPackage = s"$modelPackage.$safeDomainName",
      objPackage = modelPackage,
      referencePackage = modelPackage,
    )
  }

  def eventCtx(domain: String): ScalaChromeTypeContext = {
    ScalaChromeTypeContext(
      domain = domain,
      enumPackage = "",
      objPackage = modelPackage,
      referencePackage = modelPackage,
    )
  }

  def parameterCtx(baseCtx: ScalaChromeTypeContext): ScalaChromeTypeContext = {
    baseCtx.copy(
      enumPackage = s"${baseCtx.enumPackage}.params",
    )
  }

  def resultCtx(baseCtx: ScalaChromeTypeContext): ScalaChromeTypeContext = {
    val safeDomainName = escapeScalaVariable(baseCtx.domain)
    baseCtx.copy(
      objPackage = if (baseCtx.objPackage.isEmpty) safeDomainName else s"${baseCtx.objPackage}.$safeDomainName",
    )
  }

  // TODO do this is a nicer way
  def resultsCtx(baseCtx: ScalaChromeTypeContext): ScalaChromeTypeContext = {
    val parts = baseCtx.domain.split('.')
    val domain = s"${parts.head}.results${parts.drop(1).mkString(".")}"
    val safeDomainName = escapeScalaVariable(domain)
    baseCtx.copy(
      enumPackage = s"$modelPackage.$safeDomainName",
    )
  }

  def propertyCtx(baseCtx: ScalaChromeTypeContext, objName: String): ScalaChromeTypeContext = {
    val safeObjName = escapeScalaVariable(objName)
    baseCtx.copy(
      enumPackage = if (baseCtx.enumPackage.isEmpty) safeObjName else s"${baseCtx.enumPackage}.$safeObjName",
    )
  }
}
