package plv.colorado.edu.quantmchecker

import javax.lang.model.element.AnnotationMirror

import com.sun.source.tree._
import org.checkerframework.common.basetype.{BaseTypeChecker, BaseTypeVisitor}
import org.checkerframework.javacutil.{AnnotationBuilder, AnnotationUtils, TreeUtils}
import plv.colorado.edu.AnnoTypeUtils
import plv.colorado.edu.quantmchecker.invlang._
import plv.colorado.edu.quantmchecker.qual.ListInv
import plv.colorado.edu.quantmchecker.utils.PrintStuff

import scala.collection.JavaConverters._
import scala.collection.immutable.{HashMap, HashSet}

/**
  * @author Tianhan Lu
  */
class QuantmVisitor(checker: BaseTypeChecker) extends BaseTypeVisitor[QuantmAnnotatedTypeFactory](checker) {
  private val DEBUG_PATHS = false
  private val DEBUG_FINDINV = true
  private val DEBUG_COLLECTINV = true

  protected val LISTINV: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[ListInv])

  if (DEBUG_PATHS) {
    PrintStuff.printRedString("java.class.path: " + System.getProperty("java.class.path"))
    PrintStuff.printRedString("java.library.path: " + System.getProperty("java.library.path"))
  }

  /**
    *
    * @param classTree a class definition
    *                  Note: I have to process the source code files by myself, instead of
    *                  relying on Checker framework's visitor pattern, because I have
    *                  to know what the invariants are before type checking every statement
    */
  override def processClassTree(classTree: ClassTree): Unit = {
    val fieldInvariants: HashMap[Int, InvLangAST] = gatherClassFieldInvariants(classTree)
    if (DEBUG_FINDINV && fieldInvariants.nonEmpty) println(fieldInvariants)

    classTree.getMembers.asScala.foreach {
      case member: MethodTree =>
        // println("Visting method: ", root.getSourceFile.getName, classTree.getSimpleName, member.getName)
        val localInvariants: HashMap[Int, InvLangAST] = gatherLocalInvariants(member)
        if (DEBUG_FINDINV && localInvariants.nonEmpty) println(localInvariants)
        isInvariantPreservedInMethod(member, fieldInvariants, localInvariants)
      case member: VariableTree => // Handled by gatherClassFieldInvariants()
      case member: ClassTree => // Ignore inner classes for the moment
      case x@_ => println("Unhandled Tree kind in processClassTree(): ", x, x.getKind)
    }
    super.processClassTree(classTree)
  }

  /**
    *
    * @param classTree a class definition
    * @return a map from a line number to an invariant that might be changed by that line
    */
  private def gatherClassFieldInvariants(classTree: ClassTree): HashMap[Int, InvLangAST] = {
    classTree.getMembers.asScala.foldLeft(new HashMap[Int, InvLangAST]) {
      (acc, member) =>
        member match {
          case member: VariableTree => // Only care about variable declarations in a class
            /**
              * Get annotations on class fields
              */
            getAnnotationFromVariableTree(member) match {
              case Some(inv) => mapInvToLine(inv, acc)
              case None => acc
            }
          case _ => acc
        }
    }
  }

  /**
    *
    * @param node a method
    * @return all invariants that are declared in the method
    */
  private def gatherLocalInvariants(node: MethodTree): HashMap[Int, InvLangAST] = {
    if (node.getBody != null) {
      val stmts = node.getBody.getStatements.asScala.foldLeft(new HashSet[StatementTree]) {
        (acc, stmt) => acc ++ flattenStmt(stmt)
      }
      // if (node.getName.toString == "main") PrintStuff.printMagentaString(stmts)
      stmts.foldLeft(new HashMap[Int, InvLangAST]) {
        (acc, stmt) =>
          stmt match {
            case stmt: VariableTree =>

              /**
                * Local invariants should only be on variable declarations
                * Otherwise, invariants are simply ignored
                */
              getAnnotationFromVariableTree(stmt) match {
                case Some(inv) => mapInvToLine(inv, acc)
                case None => acc
              }
            case x@_ =>
              if (x.toString.contains("ListInv")) PrintStuff.printRedString("Missed an invariant!", x, x.getClass)
              acc
          }
      }
    } else {
      new HashMap[Int, InvLangAST]
    }
  }

  /**
    *
    * @param node     a method
    * @param fieldInv class field invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @param localInv local variable invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @return if the method violates any invariant
    */
  private def isInvariantPreservedInMethod(node: MethodTree,
                                           fieldInv: HashMap[Int, InvLangAST],
                                           localInv: HashMap[Int, InvLangAST]): Boolean = {
    if (node.getBody != null) {
      /**
        * 1. Collect all invariants (from method bodies as well as from class fields)
        * 2. Collect all method summaries
        * 3. For every statement, check if any related invariant is preserved
        * 4. For every loop, check if any related invariant is preserved throughout the loop body
        * 5. For every branch, check if any related invariant is preserved throughout the branch body
        *
        * TODO
        * 1. Override the methods that currently report type failures --- which is isSubType()
        * 2. Write annotations with ScalaZ3 and read them
        * 3. Connect upper bounds with callbacks' arguments via: method's return value is same size as method's argument
        * 4. INPUTINPUT is a special variable that represents input. On the line that decrements INPUTINPUT, do not report error even if not type check, because the decrement is meant to be non-deterministic.
        * 5. Annotate class fields
        */
      val methodAnnotations = elements.getAllAnnotationMirrors(TreeUtils.elementFromDeclaration(node)).asScala
      node.getBody.getStatements.asScala.forall { stmt => isInvariantPreservedInStmt(stmt, fieldInv, localInv) }
    } else {
      true
    }
  }

  /**
    *
    * @param node     a statement
    * @param fieldInv class field invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @param localInv local variable invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @return if the statement violates any invariant
    */
  private def isInvariantPreservedInStmt(node: StatementTree,
                                         fieldInv: HashMap[Int, InvLangAST],
                                         localInv: HashMap[Int, InvLangAST]): Boolean = {
    node match {
      case stmt: BlockTree => stmt.getStatements.asScala.forall(s => isInvariantPreservedInStmt(s, fieldInv, localInv))
      case stmt: DoWhileLoopTree =>
        val head = isInvariantPreservedInExpr(stmt.getCondition, fieldInv, localInv)
        val body = isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
        head && body
      case stmt: EnhancedForLoopTree =>
        val head = isInvariantPreservedInExpr(stmt.getExpression, fieldInv, localInv)
        val body = isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
        head && body
      case stmt: ForLoopTree =>
        val init = stmt.getInitializer.asScala.forall(s => isInvariantPreservedInStmt(s, fieldInv, localInv))
        val cond = isInvariantPreservedInExpr(stmt.getCondition, fieldInv, localInv)
        val update = stmt.getUpdate.asScala.forall(s => isInvariantPreservedInExpr(s.getExpression, fieldInv, localInv))
        val body = isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
        init && cond && update && body
      case stmt: LabeledStatementTree => isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
      case stmt: IfTree =>
        val thenBranch = isInvariantPreservedInStmt(stmt.getThenStatement, fieldInv, localInv)
        val elseBranch = isInvariantPreservedInStmt(stmt.getElseStatement, fieldInv, localInv)
        thenBranch && elseBranch
      case stmt: ExpressionStatementTree => // Base case
        isInvariantPreservedInExpr(stmt.getExpression, fieldInv, localInv)
      case stmt: VariableTree => true // Variable declaration shouldn't change invariants
      case stmt: ReturnTree => // Base case
        isInvariantPreservedInExpr(stmt.getExpression, fieldInv, localInv)
      case stmt: SwitchTree =>
        stmt.getCases.asScala.forall(caseTree => caseTree.getStatements.asScala.forall(s => isInvariantPreservedInStmt(s, fieldInv, localInv)))
      case stmt: WhileLoopTree =>
        val head = isInvariantPreservedInExpr(stmt.getCondition, fieldInv, localInv)
        val body = isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
        head && body
      case _ => true // Any other statement won't change invariant
    }
  }

  /**
    *
    * @param node a statement
    * @return line number of the statement
    */
  private def getLineNumber(node: Tree): Int = {
    val line_long = AnnoTypeUtils.getLineNumber(node, positions, root)
    assert(line_long <= Integer.MAX_VALUE, "line number overflows")
    line_long.toInt
  }

  /**
    *
    * @param node     a statement
    * @param fieldInv class field invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @param localInv local variable invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @return if the statement is in either of the maps (i.e. if the statement might
    *         change any invariant), then return the related invariant
    */
  private def mayChangeInvariant(node: Tree,
                                 fieldInv: HashMap[Int, InvLangAST],
                                 localInv: HashMap[Int, InvLangAST]): Option[InvLangAST] = {
    val line = getLineNumber(node)
    if (fieldInv.contains(line)) {
      if (localInv.contains(line)) {
        assert(false, "A line changes two invariants")
        None
      } else {
        if (DEBUG_COLLECTINV) PrintStuff.printRedString(line, node)
        fieldInv.get(line)
      }
    } else {
      if (localInv.contains(line)) {
        if (DEBUG_COLLECTINV) PrintStuff.printRedString(line, node)
        localInv.get(line)
      } else {
        // If this expression does not touch the invariant at all
        None
      }
    }
  }

  private def isInvariantPreservedInExpr(node: ExpressionTree,
                                         fieldInv: HashMap[Int, InvLangAST],
                                         localInv: HashMap[Int, InvLangAST]): Boolean = {
    mayChangeInvariant(node, fieldInv, localInv) match {
      case Some(invariant) =>
        node match {
          case expr: AnnotatedTypeTree => true // Not supported
          case expr: AnnotationTree => true // Not supported
          case expr: ArrayAccessTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: AssignmentTree =>
            // println(stmt.getVariable, stmt.getVariable.getClass)
            expr.getExpression
            true
          case expr: BinaryTree => true
          case expr: CompoundAssignmentTree => true
          case expr: ConditionalExpressionTree =>
            val t = isInvariantPreservedInExpr(expr.getTrueExpression, fieldInv, localInv)
            val f = isInvariantPreservedInExpr(expr.getFalseExpression, fieldInv, localInv)
            t && f
          case expr: ErroneousTree => true // Nothing happens
          case expr: IdentifierTree =>
            // ???
            InvWithSolver.isValidAfterUpdate(invariant, 1, 0, getLineNumber(node))
          case expr: InstanceOfTree => true // Nothing happens
          case expr: LambdaExpressionTree => true // Not supported
          case expr: LiteralTree => true // Nothing happens
          case expr: MemberReferenceTree => true // Not supported
          case expr: MemberSelectTree => true
          case expr: MethodInvocationTree => true
          case expr: NewArrayTree => true // Initializers are not supported
          case expr: NewClassTree =>
            // enclosingExpression is not supported
            expr.getArguments.asScala.forall(e => isInvariantPreservedInExpr(e, fieldInv, localInv))
          case expr: ParenthesizedTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: TypeCastTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: UnaryTree => println(expr.getKind); true
          case _ => PrintStuff.printBlueString("Expr not handled", node, node.getClass); true
        }
      case None => true
    }
  }

  /**
    *
    * @param node a statement tree
    * @return collect a set of all leave statements
    *         Note: All other StatementTrees are ignored
    */
  private def flattenStmt(node: StatementTree): HashSet[StatementTree] = {
    node match {
      case stmt: BlockTree =>
        stmt.getStatements.asScala.foldLeft(new HashSet[StatementTree])((acc, s) => acc ++ flattenStmt(s))
      case stmt: DoWhileLoopTree => flattenStmt(stmt.getStatement)
      case stmt: EnhancedForLoopTree => flattenStmt(stmt.getStatement)
      case stmt: ForLoopTree =>
        stmt.getInitializer.asScala.foldLeft(flattenStmt(stmt.getStatement)) {
          (acc, s) => acc ++ flattenStmt(s)
        }
      case stmt: WhileLoopTree => flattenStmt(stmt.getStatement)
      case stmt: LabeledStatementTree => flattenStmt(stmt.getStatement)
      case stmt: IfTree => flattenStmt(stmt.getThenStatement) ++ flattenStmt(stmt.getElseStatement)
      case stmt: SwitchTree =>
        stmt.getCases.asScala.foldLeft(new HashSet[StatementTree]) {
          (acc, caseTree) => caseTree.getStatements.asScala.foldLeft(acc)((acc2, s) => acc2 ++ flattenStmt(s))
        }
      case stmt: ExpressionStatementTree => HashSet[StatementTree](stmt)
      case stmt: ReturnTree => HashSet[StatementTree](stmt)
      case stmt: VariableTree => HashSet[StatementTree](stmt)
      case stmt: TryTree => flattenStmt(stmt.getBlock) ++ flattenStmt(stmt.getFinallyBlock)
      case stmt: SynchronizedTree => flattenStmt(stmt.getBlock)
      case _ => new HashSet[StatementTree]
    }
  }

  /**
    *
    * @param invLangAST an invariant
    * @param acc        a map from a line number to an invariant that might be changed by that line
    * @return a map that is updated by invLangAST
    */
  private def mapInvToLine(invLangAST: InvLangAST, acc: HashMap[Int, InvLangAST]): HashMap[Int, InvLangAST] = {
    invLangAST match {
      case me@Inv(remainder, self, posLine, negLine) =>
        val tmpAcc = negLine.foldLeft(acc)((acc2, line) => acc2 + (line -> me))
        posLine.foldLeft(tmpAcc)((acc2, line) => acc2 + (line -> me))
      case me@InvNoRem(self, posLine, negLine) =>
        val tmpAcc = negLine.foldLeft(acc)((acc2, line) => acc2 + (line -> me))
        posLine.foldLeft(tmpAcc)((acc2, line) => acc2 + (line -> me))
      case _ => acc
    }
  }

  /**
    *
    * @param node a variable tree
    * @return the annotation in the variable tree
    */
  private def getAnnotationFromVariableTree(node: VariableTree): Option[InvLangAST] = {
    val annotations = elements.getAllAnnotationMirrors(TreeUtils.elementFromDeclaration(node)).asScala
    /**
      * Extract annotations of ListInv type
      */
    val listInvAnnotations = annotations.filter(mirror => AnnotationUtils.areSameIgnoringValues(mirror, LISTINV))
    // val annotations: List[String] = AnnoTypeUtils.extractValues(TreeUtils.annotationFromAnnotationTree(node))
    if (listInvAnnotations.nonEmpty) {
      if (DEBUG_FINDINV) println(listInvAnnotations)
      val invs = AnnoTypeUtils.extractValues(listInvAnnotations.head)
      assert(invs.size == 1, "ListInv should only have 1 element")
      val inv = InvLangCompiler(invs.head)
      if (inv.isRight) {
        Some(inv.right.get)
      } else {
        PrintStuff.printRedString("Wrong annotation format: " + invs.head)
        None // parser error
      }
    } else {
      None
    }
  }

  @deprecated
  private def getTypeQualifiers: HashSet[AnnotationMirror] = new HashSet[AnnotationMirror] ++ atypeFactory.getQualifierHierarchy.getTypeQualifiers.asScala

  @deprecated
  private def getAnnotationMirror(node: Tree): HashSet[AnnotationMirror] = new HashSet() ++ atypeFactory.getAnnotatedType(node).getAnnotations.asScala

  @deprecated
  override def visitMethodInvocation(node: MethodInvocationTree, p: Void): Void = {
    // val invokedMethod = atypeFactory.methodFromUse(node).first
    // val typeargs = atypeFactory.methodFromUse(node).second
    // TreeUtils.elementFromTree(node)

    // val enclosingMethod: MethodTree = TreeUtils.enclosingMethod(atypeFactory.getPath(node))
    // println(elements.getAllAnnotationMirrors(TreeUtils.elementFromDeclaration(enclosingMethod)))

    super.visitMethodInvocation(node, p)
  }
}
