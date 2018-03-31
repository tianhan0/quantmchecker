package plv.colorado.edu.quantmchecker

import javax.lang.model.`type`.TypeMirror
import javax.lang.model.element.{AnnotationMirror, ExecutableElement, VariableElement}

import com.sun.source.tree._
import org.checkerframework.common.basetype.{BaseTypeChecker, BaseTypeVisitor}
import org.checkerframework.framework.`type`.AnnotatedTypeMirror
import org.checkerframework.framework.source.Result
import org.checkerframework.javacutil._
import plv.colorado.edu.Utils
import plv.colorado.edu.quantmchecker.invlang._
import plv.colorado.edu.quantmchecker.qual.{ListInv, Summary}
import plv.colorado.edu.quantmchecker.summarylang.{MethodSumUtils, MethodSummary, MethodSummaryI, MethodSummaryV}
import plv.colorado.edu.quantmchecker.utils.PrintStuff

import scala.collection.JavaConverters._
import scala.collection.immutable.{HashMap, HashSet}

/**
  * @author Tianhan Lu
  */
class QuantmVisitor(checker: BaseTypeChecker) extends BaseTypeVisitor[QuantmAnnotatedTypeFactory](checker) {
  private val DEBUG_PATHS = false
  private val DEBUG_COLLECT_INV = false
  private val DEBUG_MAY_CHANGE_INV = true
  private val DEBUG_WHICH_UNHANDLED_CASE = true

  protected val LISTINV: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[ListInv])
  protected val SUMMARY: AnnotationMirror = AnnotationBuilder.fromClass(elements, classOf[Summary])

  private val NOT_SUPPORTED = "Not supported"

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
    if (DEBUG_COLLECT_INV && fieldInvariants.nonEmpty) println(fieldInvariants)

    classTree.getMembers.asScala.foreach {
      case member: MethodTree =>
        // println("Visting method: ", root.getSourceFile.getName, classTree.getSimpleName, member.getName)
        val localInvariants: HashMap[Int, InvLangAST] = gatherLocalInvariants(member)
        if (DEBUG_COLLECT_INV && localInvariants.nonEmpty) println(localInvariants)
        val success = isInvariantPreservedInMethod(member, fieldInvariants, localInvariants)
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
            getAnnotationFromVariableTree(member, LISTINV) match {
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
              getAnnotationFromVariableTree(stmt, LISTINV) match {
                case Some(inv) => mapInvToLine(inv, acc)
                case None => acc
              }
            case x@_ =>
              if (x.toString.contains("ListInv"))
                PrintStuff.printBlueString("Missed an invariant!", x, x.getClass)
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
        * TO DO
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
    val lineNumber = getLineNumber(node)
    node match {
      case stmt: BlockTree => stmt.getStatements.asScala.forall(s => isInvariantPreservedInStmt(s, fieldInv, localInv))
      case stmt: DoWhileLoopTree =>
        val head = isInvariantPreservedInExpr(stmt.getCondition, fieldInv, localInv)
        val body = isInvariantPreservedInStmt(stmt.getStatement, fieldInv, localInv)
        head && body
      case stmt: EnhancedForLoopTree =>
        val head = relatedInvariant(stmt.getExpression, fieldInv, localInv) match {
          case Some(invariant) =>
            invariant match {
              case Inv(remainder, self, posLine, negLine) =>
                stmt.getExpression match {
                  case expr: IdentifierTree =>
                    if (remainder == expr.getName.toString) {
                      InvWithSolver.isValidAfterUpdate(invariant, -1, 0, lineNumber)
                    } else {
                      issueWarning(node, "[EnhancedForLoopTree] Not iterating over remainder is not " + NOT_SUPPORTED)
                      true
                    }
                  case expr: MethodInvocationTree =>
                    expr.getMethodSelect match {
                      case mst: MemberSelectTree =>
                        // Currently only support entrySet() in the header
                        if (mst.getIdentifier.toString == "entrySet") {
                          if (mst.getExpression.toString == remainder) {
                            InvWithSolver.isValidAfterUpdate(invariant, -1, 0, lineNumber)
                          } else {
                            issueWarning(node, "[EnhancedForLoopTree] Not iterating over remainder is not " + NOT_SUPPORTED)
                            true
                          }
                        } else {
                          issueWarning(node, "[EnhancedForLoopTree] Not invoking entrySet is not " + NOT_SUPPORTED)
                          true
                        }
                      case _ => issueWarning(node, "[EnhancedForLoopTree] " + NOT_SUPPORTED); true
                    }
                  case _ => issueWarning(node, "[EnhancedForLoopTree] " + NOT_SUPPORTED); true
                }
              case _ => issueWarning(node, "[EnhancedForLoopTree] Invariant with no remainder is " + NOT_SUPPORTED); true
            }
          case None => true
        }
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
      case stmt: SynchronizedTree => isInvariantPreservedInStmt(stmt.getBlock, fieldInv, localInv)
      case stmt: TryTree =>
        val tryBlock = isInvariantPreservedInStmt(stmt.getBlock, fieldInv, localInv)
        val finallyBlock = isInvariantPreservedInStmt(stmt.getFinallyBlock, fieldInv, localInv)
        tryBlock && finallyBlock
      case _ => true // We assert that any other statement won't change invariant
    }
  }

  private def isInvariantPreservedInExpr(node: ExpressionTree,
                                         fieldInv: HashMap[Int, InvLangAST],
                                         localInv: HashMap[Int, InvLangAST]): Boolean = {
    // TODO: We assume that the change of remainder is always towards end
    val lineNumber = getLineNumber(node)
    val success = relatedInvariant(node, fieldInv, localInv) match {
      case Some(invariant) =>
        node match {
          case expr: AnnotatedTypeTree =>
            issueWarning(node, "[AnnotatedTypeTree] " + NOT_SUPPORTED)
            true // Not supported
          case expr: AnnotationTree =>
            issueWarning(node, "[AnnotationTree] " + NOT_SUPPORTED)
            true // Not supported
          case expr: ArrayAccessTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: AssignmentTree =>

            /**
              * TODO: Currently do not support change invariants (either <remainder> or <self>)
              * via any form of assignment
              */
            val rhsPreserveInv = isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
            val (remainder, self) = invariant match {
              case Inv(remainder, self, posLine, negLine) => (Some(remainder), Some(self))
              case InvNoRem(self, posLine, negLine) => (None, Some(self))
              case _ => issueWarning(node, "[AssignmentTree] " + NOT_SUPPORTED); (None, None)
            }
            val lhsPreserveInv = self match {
              case Some(self) =>
                expr.getVariable match {
                  case i: IdentifierTree =>
                    // Guarantee from getAnnotationFromVariableTree(): the head of self
                    // is always the name of <self> under local context
                    if (i.getName.toString == self.head) {
                      issueError(node, "[AssignmentTree] Reassigning <self> is " + NOT_SUPPORTED)
                      false
                    } else {
                      remainder match {
                        case Some(remainder) =>
                          if (i.getName.toString == remainder) {
                            // We require that remainder is always defined in current method scope
                            issueError(node, "[AssignmentTree] Reassigning remainder is " + NOT_SUPPORTED)
                            false
                          } else {
                            true
                          }
                        case None => true
                      }
                    }
                  case _ => true // Otherwise, we assume LHS is side effect free
                }
              case None => true
            }
            lhsPreserveInv && rhsPreserveInv
          case expr: BinaryTree => // Every node of a binary tree should preserve the invariant
            val left = isInvariantPreservedInExpr(expr.getLeftOperand, fieldInv, localInv)
            val right = isInvariantPreservedInExpr(expr.getRightOperand, fieldInv, localInv)
            left && right
          case expr: CompoundAssignmentTree =>
            // This expression could only change remainder
            invariant match {
              case Inv(remainder, self, posLine, negLine) =>
                if (remainder == expr.getVariable.toString) {
                  expr.getExpression match {
                    case rhs: LiteralTree =>
                      rhs.getKind match {
                        case Tree.Kind.INT_LITERAL =>
                          val increment = rhs.getValue.asInstanceOf[Integer]
                          expr.getKind match {
                            case Tree.Kind.PLUS_ASSIGNMENT | Tree.Kind.MINUS_ASSIGNMENT =>
                              InvWithSolver.isValidAfterUpdate(invariant, -increment, 0, lineNumber)
                            case _ =>
                              issueWarning(node, "[CompoundAssignmentTree] Operator " + expr.getKind + " is " + NOT_SUPPORTED)
                              true // All other compound assignments are not supported
                          }
                        case _ => issueWarning(node, "[CompoundAssignmentTree] Other literal kind is " + NOT_SUPPORTED); true
                      }
                    case _ => issueWarning(node, "[CompoundAssignmentTree] Non-literal is " + NOT_SUPPORTED); true
                  }
                } else {
                  issueWarning(node, "[CompoundAssignmentTree] LHS being not remainder " + NOT_SUPPORTED)
                  true
                }
              case _ => issueWarning(node, "[CompoundAssignmentTree] Invariant with no remainder is " + NOT_SUPPORTED); true
            }
          case expr: ConditionalExpressionTree =>
            val t = isInvariantPreservedInExpr(expr.getTrueExpression, fieldInv, localInv)
            val f = isInvariantPreservedInExpr(expr.getFalseExpression, fieldInv, localInv)
            t && f
          case expr: ErroneousTree => true // Nothing happens
          case expr: IdentifierTree => true // Nothing happens
          case expr: InstanceOfTree => true // Nothing happens
          case expr: LambdaExpressionTree => // Not supported
            issueWarning(node, "[LambdaExpressionTree] " + NOT_SUPPORTED); true
          case expr: LiteralTree => true // Nothing happens
          case expr: MemberReferenceTree => // Not supported
            issueWarning(node, "[MemberReferenceTree] " + NOT_SUPPORTED); true
          case expr: MemberSelectTree => // Should not reach here
            issueWarning(node, "[MemberSelectTree] Should not reach this case"); true
          case expr: MethodInvocationTree => // Check if side effects will violate invariants
            // TODO: o.f1().f2().f3()
            val callee: ExecutableElement = getMethodElementFromInvocation(expr)
            val receiverTyp: AnnotatedTypeMirror = getTypeFactory.getReceiverType(expr)
            // atypeFactory.declarationFromElement(callee)

            val (remainder: String, self: List[String]) = invariant match {
              case Inv(remainder, self, _, _) => (remainder, self)
              case InvNoRem(self, _, _) => ("", self)
              case _ => ("", List.empty)
            }

            gatherMethodSummaries(expr) match {
              case Some(summary) =>

                val increment: Integer = summary match {
                  case MethodSummaryI(_, i) => i
                  case _ => 0 // TODO
                }

                /**
                  * Method summary could either change <remainder> or <self>
                  */
                val whichVar = MethodSumUtils.whichVar(summary, callee)
                if (whichVar.isLeft) { // Local variable is changed, according to method summary
                  val argIdx = whichVar.left.get
                  expr.getArguments.get(argIdx) match {
                    case arg: IdentifierTree => // TODO
                    case _ =>
                      // TODO
                      issueWarning(node, "[MethodInvocationTree] Complicated method arguments are " + NOT_SUPPORTED)
                  }
                } else { // Field is changed, according to method summary
                  findFieldInClass(receiverTyp.getUnderlyingType, whichVar.right.get) match {
                    case Some(field) =>
                      if (field.toString == self(1)) {
                        InvWithSolver.isValidAfterUpdate(invariant, 0, increment, lineNumber)
                      } else {
                        // TODO
                      }
                    case None => // TODO
                  }
                }
                expr.getMethodSelect match {
                  case id: IdentifierTree => true
                  case mst: MemberSelectTree => true
                  case _ => issueWarning(node, "[MethodInvocationTree] " + NOT_SUPPORTED); true
                }
              case None => // No method summaries found
                // TODO: translate library methods (e.g. append, add) into changes
                val isColAdd = Utils.isCollectionAdd(types.erasure(receiverTyp.getUnderlyingType), callee)
                // expr.getMethodSelect
                true
            }
          case expr: NewArrayTree => // Initializers are not supported
            issueWarning(node, "[NewArrayTree] Initializers are " + NOT_SUPPORTED); true
          case expr: NewClassTree => // enclosingExpression is not supported
            issueWarning(node, "[NewClassTree] enclosingExpression is not " + NOT_SUPPORTED)
            expr.getArguments.asScala.forall(e => isInvariantPreservedInExpr(e, fieldInv, localInv))
          case expr: ParenthesizedTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: TypeCastTree => isInvariantPreservedInExpr(expr.getExpression, fieldInv, localInv)
          case expr: UnaryTree =>
            // This expression could only change remainder
            invariant match {
              case Inv(remainder, self, posLine, negLine) =>
                if (remainder == expr.getExpression.toString) {
                  expr.getKind match {
                    case Tree.Kind.POSTFIX_INCREMENT
                         | Tree.Kind.PREFIX_INCREMENT
                         | Tree.Kind.POSTFIX_DECREMENT
                         | Tree.Kind.PREFIX_DECREMENT => InvWithSolver.isValidAfterUpdate(invariant, -1, 0, lineNumber)
                    case _ => issueWarning(node, "[UnaryTree] New unary operator is " + NOT_SUPPORTED); true
                  }
                } else {
                  issueWarning(node, "[UnaryTree] LHS being not remainder is " + NOT_SUPPORTED)
                  true
                }
              case _ => issueWarning(node, "[UnaryTree] " + NOT_SUPPORTED); true
            }
          case _ => PrintStuff.printBlueString("Expr not handled", node, node.getClass); true
        }
      case None => true
    }
    if (!success) {
      issueError(node, "")
    }
    success
  }

  /**
    *
    * @param node a method invocation
    * @return the element of the callee
    */
  private def getMethodElementFromInvocation(node: MethodInvocationTree): ExecutableElement = {
    atypeFactory.methodFromUse(node).first.getElement
  }

  /**
    *
    * @param typ       a class type
    * @param fieldName a field name
    * @return find the field in the class
    */
  private def findFieldInClass(typ: TypeMirror, fieldName: String): Option[VariableElement] = {
    Option(ElementUtils.findFieldInType(TypesUtils.getTypeElement(typ), fieldName))
  }

  /**
    *
    * @param node a method invocation tree
    * @return the summary of the invoked method
    */
  private def gatherMethodSummaries(node: MethodInvocationTree): Option[MethodSummary] = {
    val invokedMethod: ExecutableElement = getMethodElementFromInvocation(node)
    val summaries = atypeFactory.getDeclAnnotations(invokedMethod).asScala.filter(mirror => AnnotationUtils.areSameIgnoringValues(mirror, SUMMARY))
    if (summaries.size == 1) {
      val summaryList = Utils.extractValues(summaries.head)
      if (summaryList.size != 2) {
        issueWarning(invokedMethod, "Method summary should have exactly 2 arguments")
        None
      } else {
        if (summaryList(1).forall(c => c.isDigit)) {
          Some(MethodSummaryI(summaryList.head, Integer.parseInt(summaryList(1))))
        } else {
          Some(MethodSummaryV(summaryList.head, summaryList(1)))
        }
      }
    } else {
      if (summaries.isEmpty)
        None
      else {
        issueWarning(invokedMethod, "Method should have exactly 1 summary")
        None
      }
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
    * @param node a statement
    * @return line number of the statement
    */
  private def getLineNumber(node: Tree): Int = {
    val line_long = Utils.getLineNumber(node, positions, root)
    assert(line_long <= Integer.MAX_VALUE, "line number overflows")
    line_long.toInt
  }

  /**
    *
    * @param node     an expression
    * @param fieldInv class field invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @param localInv local variable invariants: a map from a line number to an invariant
    *                 that might be changed by that line
    * @return if the statement is in either of the maps (i.e. if the statement might
    *         change any invariant), then return the related invariant
    */
  private def relatedInvariant(node: ExpressionTree,
                               fieldInv: HashMap[Int, InvLangAST],
                               localInv: HashMap[Int, InvLangAST]): Option[InvLangAST] = {
    val line = getLineNumber(node)
    if (fieldInv.contains(line)) {
      if (localInv.contains(line)) {
        assert(false, "A line changes two invariants")
        None
      } else {
        if (DEBUG_MAY_CHANGE_INV) PrintStuff.printRedString(line, node)
        fieldInv.get(line)
      }
    } else {
      if (localInv.contains(line)) {
        if (DEBUG_MAY_CHANGE_INV) PrintStuff.printRedString(line, node)
        localInv.get(line)
      } else {
        // If this expression is not supposed not touch the invariant at all
        // TODO: What if this expression indeed touch the invariant?
        None
      }
    }
  }

  private def issueWarning(node: Object, msg: String): Unit = {
    // Debug only: I want to know which unhandled case issues the warning
    if (DEBUG_WHICH_UNHANDLED_CASE) {
      val trace = Thread.currentThread().getStackTrace.toList.filter(s => s.toString.contains("QuantmVisitor.scala"))(1)
      PrintStuff.printGreenString("[WARNING]" + trace.getFileName + ": " + trace.getLineNumber)
    }
    checker.report(Result.warning(msg), node)
  }

  private def issueError(node: Object, msg: String): Unit = {
    if (DEBUG_WHICH_UNHANDLED_CASE) {
      val trace = Thread.currentThread().getStackTrace.toList.filter(s => s.toString.contains("QuantmVisitor.scala"))(1)
      PrintStuff.printGreenString("[ERROR]" + trace.getFileName + ": " + trace.getLineNumber)
    }
    checker.report(Result.failure(msg), node)
  }

  /**
    *
    * @param node  a variable tree
    * @param annot which annotation to collect
    * @return collect the specified type of annotations in the variable tree
    */
  private def getAnnotationFromVariableTree(node: VariableTree, annot: AnnotationMirror): Option[InvLangAST] = {
    val annotations = elements.getAllAnnotationMirrors(TreeUtils.elementFromDeclaration(node)).asScala
    /**
      * Extract annotations of ListInv type
      */
    val listInvAnnotations = annotations.filter(mirror => AnnotationUtils.areSameIgnoringValues(mirror, annot))
    // val annotations: List[String] = AnnoTypeUtils.extractValues(TreeUtils.annotationFromAnnotationTree(node))
    if (listInvAnnotations.nonEmpty) {
      if (DEBUG_COLLECT_INV) println(listInvAnnotations)
      val invs = Utils.extractValues(listInvAnnotations.head)
      assert(invs.size == 1, "ListInv should only have 1 element")
      val inv = InvLangCompiler(invs.head)
      if (inv.isRight) {
        val name = node.getName.toString
        inv.right.get match {
          case Inv(remainder, self, posLine, negLine) => Some(Inv(remainder, name :: self, posLine, negLine))
          case InvNoRem(self, posLine, negLine) => Some(InvNoRem(name :: self, posLine, negLine))
          case _ => issueWarning(node, "Wrong annotation format"); None
        }
      } else {
        issueWarning(node, "Wrong annotation format")
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

  /** Places to look for help:
    * 1. TreeUtils.xxx
    * TreeUtils.elementFromTree(node)
    * val enclosingMethod: MethodTree = TreeUtils.enclosingMethod(atypeFactory.getPath(node))
    * 2. this.checker.xxx
    * 3. atypeFactory.xxx
    * 4. elements.xxx
    * 5. trees.xxx
    * 6. types.xxx
    */
}
