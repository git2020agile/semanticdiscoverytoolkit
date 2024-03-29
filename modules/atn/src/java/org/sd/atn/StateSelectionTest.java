/*
    Copyright 2011 Semantic Discovery, Inc. (www.semanticdiscovery.com)

    This file is part of the Semantic Discovery Toolkit.

    The Semantic Discovery Toolkit is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The Semantic Discovery Toolkit is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The Semantic Discovery Toolkit.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.sd.atn;


import java.util.ArrayList;
import java.util.List;
import org.sd.token.Token;
import org.sd.util.Usage;
import org.sd.util.range.IntegerRange;
import org.sd.xml.DomElement;
import org.sd.xml.DomNode;
import org.w3c.dom.NodeList;

/**
 * A BaseClassifierTest to (successively) locate states and apply a TokenTest
 * on each. Once a state is located and passes the token test, it becomes the
 * reference state from which the next state is sought.
 * <p>
 * @author Spence Koehler
 */
//todo: add @Usage(notes =)
public class StateSelectionTest extends BaseClassifierTest {
  
  //
  // Model:
  // - There are 2 active models being referenced for state selection.
  //   - (1) The state tree, which holds the series of states created while transitioning through the machine according to the grammar and an input
  //     - There are essentially three kinds of states
  //       - (1) push states: states representing the transition to deeper rules (descending)
  //       - (2) match states: states representing classifier matches to tokens
  //       - (3) pop states: states representing the transition back up to the pushing rule (ascending)
  //       - NOTE: a fourth category of "unmatched states" exists, but these only serve to show paths that do not lead to successful parses.
  //       - TERMINOLOGY: ascension and descension correspond to moving "up" and "down" in the parse tree whose single-category root is at the top (ascension) and terminal categorized token leaves are at the bottom (descension).
  //     - The state tree holds paths for successful state transitions.
  //       - Alternate paths correspond to alternate parses.
  //       - The path in the state tree from any state node to the root corresponds to a single unambiguous parse
  //         - NOTE: this terminal-to-root path is what is being referenced as the active sequence of states accessed through this class
  //     - For rule steps that reference other rules, the state transition path is:
  //       - from a push state for the rule
  //       - to the states for each of the rule's steps
  //       - to the pop state for the rule
  //   - (2) The parse tree being generated by the grammar for an input for a successful state path
  //     - a push state with its corresponding pop state form a single constituent (non-terminal) parse tree node
  //       - and corresponds to a single rule-referencing rule-step in the grammar.
  //       - The push state holds the first token matched in a (successfully) pushed rule
  //         - and is considered to be at the level of the rule steps in its bundling rule (constituent).
  //       - The pop state holds the last token matched in a (successfully) pushed rule
  //         - and is (also) considered to be at the level of the rule steps in its bundling rule (constituent).
  //     - a match state with its corresponding token forms a terminal parse tree node with its category and text
  //       - and corresponds to a single classification-referencing rule-step in the grammar.
  //       - The match state is at a level deeper than the rule that "pushed" its constituent.
  // - Definitions:
  //   - A "constituent" rule step is a rule step (the "pushing" rule step) that references another rule (the "pushed" rule).
  //     - The parse tree node generated by the machine when matching a constituent rule step will be the parent to the pushed rule's step's nodes.
  //     - A "constituent" rule step has 4 associated state nodes:
  //       - the "push state" (at the level of the the pushing rule step) with the first constituent token
  //       - the "first match state" for the first matching token (one level deeper from the pushing rule step)
  //       - the "last match state" for the last matching token (one level deeper from the pushing rule step)
  //       - the "pop state" (at the level of the pushing rule step) with the last constituent token
  //       - NOTE: the push and first match states hold the same (first constituent) token and the last match and pop states hold the same (last constituent) token.
  //   - A "match" rule step is a rule step that matches a token using a classification and becomes a terminal parse tree node (with the token text)
  //     - Where classifications come from
  //       - classifiers
  //       - prior parses
  //       - literal grammar matches
  //       - NOTE: See the TokenTest for accessing deeper information on classified tokens relating to classification source, etc.
  //   - "Gravity" refers to which state to select for a constituent:
  //     - "pop" (default) selects the constituent's pop state (which holds the last match token)
  //     - "lastMatch" selects the constituent's last match state (whose token matches the "pop" state token)
  //     - "firstMatch" selects the constituent's first match state (which holds the first match token for the constituent)
  //     - "push" selects the constituent's push state (whose token matches the "firstMatch" state token)
  //     - NOTE: gravity on a match state has no effect since there is only one associated state.
  //
  // <test>
  //   <jclass>org.sd.atn.StateSelectionTest</jclass>
  //   <selectState
  //     path="x[R].y[R].z[R]"        // where x,y,z are (parse tree) categories and/or labels and @=current category, *=any category; default is unconstrained (empty)
  //                                  // R is an integer range (0-based) of acceptable indexes
  //     tokenDistance="T"            // T is a (positive) token distance either absolute or a range relative to current state token, where 0 is current token; default is unbounded
  //     constituentDistance="C"      // C is a (positive) state distance either absolute or a range relative to current state where 0 is the current state and states are counted as +1 for a match or constituent at the current level or higher (but deeper constituents if traversed count for 0); default is unbounded
  //     distance="D"                 // sets the constituentDistance to D and tokenDistance to unbounded
  //     ascend='true|false|L'        // L is a (positive) level distance either absolute or a range relative to current level; default is false
  //     descend='true|false|L'       // default is "false"
  //     unlock='true|false|L'        // sets both ascend and descend
  //     gravity='pop|lastMatch|firstMatch|push'  // identifies which state to select for a constituent node
  //     closestOnly='true|false'     // default=true; if false, searches for other states matching all of the criterea when token tests fail
  //     disallow='false|true'>       // default=false; if true, fails the test when state is found; otherwise, reference state remains unchanged
  //       <test>...</test>
  //       ...more tests...
  //   </selectState>
  //   ...more select state terms that work from the last selected state...
  // </test>
  //
  // Examples:
  //   <selectState distance="0" />  // select the current state (which is pop state with the last token of constituent if at a constituent state)
  //   <selectState distance="0" gravity="push" />  // select the current state or its push state (with the first token of constituent)
  //   <selectState distance="0" gravity="firstMatch" />  // select the current constituent's first match state
  //   <selectState distance="1" gravity="firstMatch" />  // select the prior state's (in the current constituent) first match state
  //   <selectState distance="1" path="@" />      // select the prior repeat state (matching current)
  //   <selectState path="@[0]" />                // select (pop state of) the first repeat state (matching current)
  //   <selectState path="@[0]" distance="-2"/>   // select (pop state of) the first repeat state (matching current), but not if 3 or more away from current
  //   <selectState distance="1" unlock="true"/>  // select the prior matched state, even if outside the current constituent
  //   <selectState distance="1" unlock="true" gravity="firstMatch" />  // select prior matched state's first match state
  //   <selectState path="*.@" ascend="1" gravity="push" />    // select the parent constituent's push state (which has the same token as the first match state in the current consituent)
  //   <selectState path="*[0]" gravity="firstMatch" /> // select the parent constituent's first match state
  // </test>
  //

  private boolean verbose;
  private List<StateSelector> stateSelectors;

  public StateSelectionTest(DomNode defNode, ResourceManager resourceManager) {
    super(defNode, resourceManager);
    this.verbose = defNode.getAttributeBoolean("verbose", false);
    this.stateSelectors = new ArrayList<StateSelector>();

    final NodeList selectorNodes = defNode.selectNodes("selectState");
    for (int nodeNum = 0; nodeNum < selectorNodes.getLength(); ++nodeNum) {
      final DomElement selectorElt = (DomElement)selectorNodes.item(nodeNum);
      final StateSelector stateSelector = new StateSelector(selectorElt, resourceManager);
      stateSelectors.add(stateSelector);
    }
  }

  protected void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  protected boolean isVerbose() {
    return verbose;
  }


  protected boolean doAccept(Token token, AtnState curState) {
    boolean result = true;

    AtnState nextState = curState;
    int selectorNum = 1;
    for (StateSelector stateSelector : stateSelectors) {
      if (verbose) {
        System.out.println("***StateSelectionTest applying selector #" + selectorNum + "/" + stateSelectors.size() + " to state=" + nextState);
      }

      nextState = stateSelector.findSelectedState(nextState);

      if (verbose) {
        System.out.print("***StateSelectionTest selector #" + selectorNum + "/" + stateSelectors.size());
      }

      if (nextState == null) {
        if (verbose) {
          System.out.println(" FAILED");
        }

        result = false;
        break;
      }
      else if (verbose) {
        System.out.println(" SUCCEEDED. nextState=" + nextState);
      }

      ++selectorNum;
    }

    return result;
  }


  private static class StateSelector {
    private boolean verbose;
    private StateTestContainer testContainer;
    private PathMatcher pathMatcher;
    private IntegerRange tokenDistance;
    private IntegerRange constituentDistance;
    private IntegerRange ascend;
    private IntegerRange descend;
    private AtnStateUtil.Gravity gravity;
    private boolean closestOnly;
    private boolean disallow;
    private boolean hasArg;

    protected StateSelector(DomElement selectElement, ResourceManager resourceManager) {
      this.hasArg = false;

      // verbosity
      this.verbose =
        selectElement.getAttributeBoolean("verbose", false) ||
        ((DomElement)(selectElement.getParentNode())).getAttributeBoolean("verbose", false);

      // tests
      this.testContainer = new StateTestContainer(selectElement, resourceManager);

      // path
      this.pathMatcher = buildPathMatcher(selectElement.getAttributeValue("path", null));
      if (pathMatcher != null) hasArg = true;

      // tokenDistance, constituentDistance, distance
      this.tokenDistance = this.constituentDistance = null;
      final IntegerRange distance = getAttributeRange(selectElement, "distance", null);
      if (distance != null) {  // sets tokenDistance to unbounded and constituentDistance to D
        this.constituentDistance = distance;
      }
      this.tokenDistance = getAttributeRange(selectElement, "tokenDistance", this.tokenDistance);
      this.constituentDistance = getAttributeRange(selectElement, "tokenDistance", this.constituentDistance);
      if (tokenDistance != null || constituentDistance != null) hasArg = true;

      // ascend, descend, unlock
      this.ascend = this.descend = new IntegerRange(0);

      if (selectElement.hasAttribute("unlock")) {
        // sets both ascend and descend
        final IntegerRange unlock = getAttributeRange(selectElement, "unlock", null);
        this.ascend = this.descend = unlock;
        hasArg = true;
      }
      this.ascend = getAttributeRange(selectElement, "ascend", this.ascend);
      this.descend = getAttributeRange(selectElement, "descend", this.descend);
      if (selectElement.hasAttribute("ascend") || selectElement.hasAttribute("descend")) hasArg = true;

      // gravity
      this.gravity = buildGravity(selectElement.getAttributeValue("gravity", null));

      // closestOnly, disallow
      this.closestOnly = selectElement.getAttributeBoolean("closestOnly", true);
      this.disallow = selectElement.getAttributeBoolean("disallow", false);

      // open up range constraints if there were no args
      if (!hasArg) {
        this.tokenDistance = this.constituentDistance = null;
      }
    }

    private final AtnStateUtil.Gravity buildGravity(String gravityString) {
      AtnStateUtil.Gravity result = null;

      if (gravityString != null && !"".equals(gravityString)) {
        result = AtnStateUtil.GRAVITY_LOOKUP.get(gravityString.toLowerCase());
      }

      return result == null ? AtnStateUtil.Gravity.POP : result;
    }

    private final IntegerRange getAttributeRange(DomElement elt, String att, IntegerRange defaultValue) {
      IntegerRange result = defaultValue;

      final String rangeString = elt.getAttributeValue(att, null);
      if (rangeString != null) {
        if ("true".equals(rangeString)) {
          // interpret "true" as an unbounded range (null)
          result = null;
        }
        else if ("false".equals(rangeString)) {
          // interpret "false" as a range that only accepts "0"
          result = new IntegerRange(0);
        }
        else {
          result = new IntegerRange(rangeString);
        }
      }

      return result;
    }


    private final PathMatcher buildPathMatcher(String pathString) {
      PathMatcher result = null;

      if (pathString != null && !"".equals(pathString)) {
        result = new PathMatcher(pathString);
      }

      return result;
    }


    /** Find the state defined by this instance from startState, or null. */
    public AtnState findSelectedState(AtnState fromState) {
      AtnState result = null;

      int ascendDist = 0;
      int descendDist = 0;
      int tokenDist = 0;
      int constituentDist = 0;
      Token prevToken = null;
      final int fromDepth = AtnStateUtil.getPushDepth(fromState);

      for (AtnState curState = fromState; curState != null; curState = curState.getParentState()) {

        if (verbose) {
          System.out.println("***StateSelectionTest.StateSelector visitingState(" +
                             curState + ") ascend=" + ascendDist + " descend=" +
                             descendDist + " tokenDist=" + tokenDist +
                             " constituentDist=" + constituentDist);
        }

        // meet vertical, distance, and path constraints
        if (hasArg && meetsRangeConstraint(ascendDist, ascend) &&
            meetsRangeConstraint(descendDist, descend) &&
            meetsRangeConstraint(tokenDist, tokenDistance) &&
            meetsRangeConstraint(constituentDist, constituentDistance) &&
            meetsPathConstraint(curState, fromState)) {  // apply final tests

          // adjust according to gravity
          final AtnState theState = AtnStateUtil.adjustForGravity(curState, gravity);

          if (testContainer.hasFinalTests()) {

            if (verbose) {
              System.out.println("***StateSelectionTest.StateSelector verifyingFinalTests(" +
                                 theState + ") ascend=" + ascendDist + " descend=" +
                                 descendDist + " tokenDist=" + tokenDist +
                                 " constituentDist=" + constituentDist);
            }

            // verify tests
            final StateTestContainer.Directive directive = verifyFinalTests(theState);
            if (directive != StateTestContainer.Directive.CONTINUE) {
              switch (directive) {
              case SUCCEED :
                result = theState; break;
              case FAIL :
                result = null; break;
              }

              if (verbose) {
                System.out.println("***StateSelectionTest.StateSelector final tests terminating w/" + directive + " at state=" + theState);
              }

              break;
            }
          }
          else {
            // no final tests, but all constraints met ==> success
            result = theState;
            break;
          }
        }
        else if (testContainer.hasScanTests()) {  // apply scanning tests
          // adjust according to gravity
          final AtnState theState = AtnStateUtil.adjustForGravity(curState, gravity);

          if (verbose) {
            System.out.println("***StateSelectionTest.StateSelector verifyingScanTests(" +
                               theState + ") ascend=" + ascendDist + " descend=" +
                               descendDist + " tokenDist=" + tokenDist +
                               " constituentDist=" + constituentDist);
          }

          // verify tests
          final StateTestContainer.Directive directive = verifyScanTests(theState);
          if (directive != StateTestContainer.Directive.CONTINUE) {
            switch (directive) {
              case SUCCEED :
                result = theState; break;
              case FAIL :
                result = null; break;
            }

            if (verbose) {
              System.out.println("***StateSelectionTest.StateSelector scan tests terminating w/" + directive + " at state=" + theState);
            }

            break;
          }
        }

        // increment distances
        final int curDepth = AtnStateUtil.getPushDepth(curState);
        if (curDepth >= fromDepth) {  // descending or level
          ascendDist = 0;
          descendDist = curDepth - fromDepth;
        }
        else if (curDepth < fromDepth) {  // ascending
          descendDist = 0;
          ascendDist = fromDepth - curDepth;
        }

        if (curState.getInputToken() != prevToken) {
          ++tokenDist;
        }

        // adjust consituentDist
        if (curState.isPoppedState() && ascendDist >= 0) ++constituentDist;

        // preserve token
        prevToken = curState.getInputToken();
      }

      if (disallow) {
        if (result == null) {
          // didn't find a disallowed state, so we succeed at fromState
          result = fromState;
        }
        else {
          // found a disallowed state, so result is null for "fail"
          result = null;
        }

        if (verbose) {
          System.out.println("***StateSelectionTest.StateSelector 'disallow' flipping result to " + result);
        }
      }

      return result;
    }

    private final boolean meetsRangeConstraint(int value, IntegerRange range) {
      boolean result = true;

      if (range != null) {
        result = range.includes(value);
      }

      return result;
    }

    private final boolean meetsPathConstraint(AtnState theState, AtnState curState) {
      boolean result = true;

      if (pathMatcher != null) {
        result = pathMatcher.matches(theState, curState);
      }

      return result;
    }

    private final StateTestContainer.Directive verifyScanTests(AtnState theState) {
      return testContainer.verifyScanTests(theState.getInputToken(), theState);
    }

    private final StateTestContainer.Directive verifyFinalTests(AtnState theState) {
      return testContainer.verifyFinalTests(theState.getInputToken(), theState);
    }
  }

  private static final class PathMatcher {

    private PathComponent[] pathComponents;

    public PathMatcher(String pathString) {
      final String[] pieces = pathString.split("\\.");
      this.pathComponents = new PathComponent[pieces.length];
      for (int i = 0; i < pieces.length; ++i) {
        this.pathComponents[i] = new PathComponent(pieces[i]);
      }
    }

    public boolean matches(AtnState theState, AtnState curState) {
      return matches(theState, curState, pathComponents.length - 1);
    }

    private final boolean matches(AtnState theState, AtnState curState, int pathComponentIndex) {
      boolean result = true;

      if (pathComponentIndex < 0) return result;
      final PathComponent pathComponent = pathComponents[pathComponentIndex];
      boolean checkNextComponent = true;

      // check for current match
      if (pathComponent.isCurrent()) {
        result =
          ((theState == curState) ||
           theState.getRuleStep().getLabel().equals(curState.getRuleStep().getLabel())) &&
          pathComponent.subscriptMatches(theState);
      }
      // check for wild match by recursing now for each possible next (parent push) state
      else if (pathComponent.isWild()) {
        // for double wild, find first match of theState and its parents to remaining pathComponents
        if (pathComponent.isDoubleWild()) {
          checkNextComponent = false;
          if (pathComponentIndex > 0) {
            result = false;
            for (AtnState nextState = theState; nextState != null; nextState = nextState.getPushState()) {
              if (matches(nextState, curState, pathComponentIndex - 1)) {
                result = true;
                break;
              }
            }
          }
        }

        // for both wild and double-wild, verify subscript match
        if (result) {
          result = pathComponent.subscriptMatches(theState);
        }
      }
      // check for pattern (and subscript) match
      else {
        result = pathComponent.patternMatches(theState);
      }

      // increment "theState" and decrement pathComponentIndex (recurse)
      if (result && checkNextComponent && pathComponentIndex > 0) {
        theState = theState.getPushState();

        if (theState == null) {
          result = false;
        }
        else {
          result = matches(theState, curState, pathComponentIndex - 1);
        }
      }

      return result;
    }
  }

  private static final class PathComponent {

    private String pattern;
    private boolean wild;
    private boolean doubleWild;
    private boolean current;
    private IntegerRange subscriptRange;

    public PathComponent(String pathComponentString) {
      this.pattern = null;
      this.wild = false;
      this.doubleWild = false;
      this.current = true;
      this.subscriptRange = null;

      if (pathComponentString != null && !"".equals(pathComponentString)) {
        this.pattern = pathComponentString;

        final int lbPos = pathComponentString.indexOf('[');
        if (lbPos >= 0) {
          final int rbPos = pathComponentString.indexOf(']', lbPos + 1);
          if (rbPos >= 0) {
            this.pattern = pathComponentString.substring(0, lbPos);
            this.subscriptRange = new IntegerRange(pathComponentString.substring(lbPos + 1, rbPos));
          }
        }

        if ("@".equals(this.pattern)) {
          this.current = true;
        }
        else if ("*".equals(this.pattern)) {
          this.wild = true;
        }
        else if ("**".equals(this.pattern)) {
          this.wild = true;
          this.doubleWild = true;
        }
      }
    }

    public String getPattern() {
      return pattern;
    }

    public boolean isWild() {
      return wild;
    }

    public boolean isDoubleWild() {
      return doubleWild;
    }

    public boolean isCurrent() {
      return current;
    }

    public IntegerRange getSubscriptRange() {
      return subscriptRange;
    }

    public boolean patternMatches(AtnState atnState) {
      boolean result = false;

      final AtnRuleStep ruleStep = atnState.getRuleStep();
      result = ruleStep.getLabel().equals(pattern) || ruleStep.getCategory().equals(pattern);

      if (result) {
        result = subscriptMatches(atnState);
      }

      return result;
    }

    public boolean subscriptMatches(AtnState atnState) {
      boolean result = true;

      if (subscriptRange != null) {
        final int index = computeStateIndex(atnState);
        result = subscriptRange.includes(index);
      }

      return result;
    }

    private final int computeStateIndex(AtnState atnState) {
      int result = 0;

      String curLabel = atnState.getRuleStep().getLabel();
      for (AtnState curState = AtnStateUtil.getPriorConstituentState(atnState, AtnStateUtil.Gravity.PUSH); curState != null;
           curState = AtnStateUtil.getPriorConstituentState(curState, AtnStateUtil.Gravity.PUSH)) {
        if (current || !wild) {
          if (!curLabel.equals(curState.getRuleStep().getLabel())) {
            break;
          }
        }
        ++result;
      }

      return result;
    }
  }
}
