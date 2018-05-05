package plv.colorado.edu.quantmchecker

import javax.lang.model.element.AnnotationMirror

import com.sun.source.tree.Tree
import org.checkerframework.common.basetype.{BaseAnnotatedTypeFactory, BaseTypeChecker}
import org.checkerframework.framework.`type`.QualifierHierarchy
import org.checkerframework.framework.flow.{CFAbstractAnalysis, CFStore, CFTransfer, CFValue}
import org.checkerframework.framework.util.{GraphQualifierHierarchy, MultiGraphQualifierHierarchy}
import org.checkerframework.javacutil.{AnnotationBuilder, AnnotationUtils}
import plv.colorado.edu.Utils
import plv.colorado.edu.quantmchecker.qual.{Inv, InvBot, Rem}

/**
  * @author Tianhan Lu
  */
class QuantmAnnotatedTypeFactory(checker: BaseTypeChecker) extends BaseAnnotatedTypeFactory(checker) {
  private val DEBUG: Boolean = false
  protected val INV: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[Inv])
  protected val REM: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[Rem])
  protected val INVBOT: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[InvBot])

  // disable flow inference
  // super(checker, false);
  this.postInit()
  if (DEBUG) {
    println(getQualifierHierarchy.toString)
    // getTypeHierarchy();
  }

  override def createFlowTransferFunction(analysis: CFAbstractAnalysis[CFValue, CFStore, CFTransfer]): CFTransfer = {
    new QuantmTransfer(analysis)
  }

  // Learned from KeyForAnnotatedTypeFactory.java
  override def createQualifierHierarchy(factory: MultiGraphQualifierHierarchy.MultiGraphFactory): QualifierHierarchy = new QuantmQualifierHierarchy(factory)

  /**
    *
    * @param rcvr
    * @return annotation of the receiver of a method invocation
    */
  def getTypeAnnotation(rcvr: Tree): AnnotationMirror = {
    this.getQualifierHierarchy()
      .findAnnotationInHierarchy(
        getAnnotatedType(rcvr).getAnnotations(),
        this.getQualifierHierarchy().getTopAnnotations().iterator().next())
  }

  final private class QuantmQualifierHierarchy(val factory: MultiGraphQualifierHierarchy.MultiGraphFactory) extends GraphQualifierHierarchy(factory, INVBOT) {
    override def isSubtype(subAnno: AnnotationMirror, superAnno: AnnotationMirror): Boolean = {
      val isSubInv = AnnotationUtils.areSameIgnoringValues(subAnno, INV)
      val isSubRem = AnnotationUtils.areSameIgnoringValues(subAnno, REM)
      val isSuperInv = AnnotationUtils.areSameIgnoringValues(superAnno, INV)
      val isSuperRem = AnnotationUtils.areSameIgnoringValues(superAnno, REM)

      if (isSubInv && isSuperInv) {
        val lhsValues = Utils.extractArrayValues(superAnno, "value")
        val rhsValues = Utils.extractArrayValues(subAnno, "value")
        return lhsValues == rhsValues
      }

      if (isSubRem && isSuperRem) {
        val lhsValues = Utils.extractValue(superAnno, "value")
        val rhsValues = Utils.extractValue(subAnno, "value")
        return lhsValues == rhsValues
      }

      val newSuperAnno = if (isSuperInv) INV else if (isSuperRem) REM else superAnno
      val newSubAnno = if (isSubInv) INV else if (isSubRem) REM else subAnno
      if (DEBUG) {
        println(subAnno, newSubAnno)
        println(superAnno, newSuperAnno)
      }
      super.isSubtype(newSubAnno, newSuperAnno)
    }
  }

}
