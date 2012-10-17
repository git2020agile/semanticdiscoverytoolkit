/*
    Copyright 2010 Semantic Discovery, Inc. (www.semanticdiscovery.com)

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sd.token.CategorizedToken;
import org.sd.token.Feature;
import org.sd.token.KeyLabel;
import org.sd.util.Usage;
import org.sd.util.tree.Tree;
import org.sd.xml.DataProperties;
import org.sd.xml.DomElement;
import org.sd.xml.DomNode;
import org.sd.xml.XmlLite;

/**
 * An ParseInterpreter that uses the parse itself as the interpretation.
 * <p>
 * @author Spence Koehler
 */
@Usage(notes =
       "An org.sd.atn.ParseInterpreter implementation that transforms\n" +
       "a parse tree directly into an interpretation."
  )
public class IdentityParseInterpreter implements ParseInterpreter {
  
  private boolean compress;

  public IdentityParseInterpreter(DomNode domNode, ResourceManager resourceManager) {
    this.compress = domNode.getAttributeBoolean("compress", false);
  }

  public IdentityParseInterpreter(boolean compress) {
    this.compress = compress;
  }

  /**
   * Get classifications offered by this interpreter.
   * 
   * Note that classifications are applied to parse-based tokens. Access
   * to the potential classifications is intended to help with monitoring,
   * introspection, and other high-level tools for building grammars and
   * parsers.
   */
  public String[] getClassifications() {
    return new String[]{"identity"};
  }

  /**
   * Get the interpretations for the parse or null.
   */
  public List<ParseInterpretation> getInterpretations(Parse parse, DataProperties overrides) {
    final List<ParseInterpretation> result = new ArrayList<ParseInterpretation>();
    final Tree<XmlLite.Data> interpTree = asInterpTree(parse.getParseTree(), null);
    result.add(new ParseInterpretation(interpTree));
    return result;
  }

  /**
   * Supplement this interpreter according to the given domElement.
   */
  public void supplement(DomElement domElement) {
    // nothing to do.
  }

  private final Tree<XmlLite.Data> asInterpTree(Tree<String> parseTree, Tree<XmlLite.Data> parent) {
    Set<Tree<String>> compressNodes = null;

    if (compress) {
      compressNodes = getCompressNodes(parseTree);
    }

    final Tree<XmlLite.Data> result = convertTree(parseTree, parent, compressNodes);
    return result;
  }

  private final Tree<XmlLite.Data> convertTree(Tree<String> parseTree, Tree<XmlLite.Data> parent, Set<Tree<String>> compressNodes) {

    boolean isTag = parseTree.hasChildren();
    boolean recurse = isTag;
    Tree<XmlLite.Data> curInterpNode = null;

    if (isTag) {
      String nodeText = parseTree.getData();
      if ("?".equals(nodeText)) nodeText = "_UNK_";
      curInterpNode = XmlLite.createTagNode(nodeText);
      ParseInterpretation parseInterp = null;

      // add attributes
      if (parseTree.hasAttributes()) {
        final XmlLite.Tag tag = curInterpNode.getData().asTag();

        for (Map.Entry<String, Object> entry : parseTree.getAttributes().entrySet()) {
          final String attr = entry.getKey();
          final Object val = entry.getValue();

          if (val != null) {
            if (val instanceof CategorizedToken) {
              final CategorizedToken cToken = (CategorizedToken)val;

              //NOTE: escaping of attribute values done within XmlLite.Tag

              // tokPreDelim, tokPostDelim, tokKeyLabels
              final String preDelim = cToken.token.getPreDelim();
              final String postDelim = cToken.token.getPostDelim();
              final String keyLabels = KeyLabel.asString(cToken.token.getKeyLabels());

              if (!"".equals(preDelim)) {
                tag.attributes.put("_tokPreDelim", preDelim);
              }
              tag.attributes.put("_tokText", cToken.token.getText());
              if (!"".equals(postDelim)) {
                tag.attributes.put("_tokPostDelim", postDelim);
              }
              if (!"".equals(keyLabels)) {
                tag.attributes.put("_tokKeyLabels", keyLabels);
              }

              // add-in token features
              if (cToken.token.hasFeatures()) {
                for (Feature feature : cToken.token.getFeatures().getFeatures()) {
                  final Object featureValue = feature.getValue();
                  if (featureValue != null) {
                    final String className = featureValue.getClass().getName();

                    // just include "primitive" feature values
                    if (className.startsWith("java.lang")) {
                      tag.attributes.put(feature.getType(), featureValue.toString());
                    }
                    else if (featureValue instanceof ParseInterpretation) {
                      parseInterp = (ParseInterpretation)featureValue;
                    }
                  }
                }
              }
            }
            else {
              tag.attributes.put(attr, val.toString());
            }
          }
        }
      }

      if (parseInterp != null) {
        curInterpNode = parseInterp.getInterpTree();
        recurse = false;
      }

      if (compressNodes != null && compressNodes.contains(parseTree)) {
        if (parent != null) {
          parent.addChild(curInterpNode);
        }
        parent = curInterpNode;
        isTag = recurse = false;
      }
    }

    if (!isTag) {
      final CategorizedToken cToken = ParseInterpretationUtil.getCategorizedToken(parseTree);

      String text = null;
      if (cToken != null) {
        text = cToken.token.getTextWithDelims();
      }
      else {
        text = parseTree.getData();
      }

      curInterpNode = XmlLite.createTextNode(text);
    }

    // construct tree
    if (parent != null) {
      parent.addChild(curInterpNode);
    }

    // recurse
    if (recurse) {
      // add children
      for (Tree<String> childTree : parseTree.getChildren()) {
        convertTree(childTree, curInterpNode, compressNodes);
      }
    }

    return curInterpNode;
  }

  private final Set<Tree<String>> getCompressNodes(Tree<String> parseTree) {
    final Set<Tree<String>> result = new HashSet<Tree<String>>();
    final Set<Tree<String>> gpNodes = new HashSet<Tree<String>>();

    // collect nodes 2 above the leaves
    final List<Tree<String>> leaves = parseTree.gatherLeaves();
    for (Tree<String> leaf : leaves) {
      final Tree<String> parent = leaf.getParent();
      if (parent != null) {
        final Tree<String> gparent = parent.getParent();
        if (gparent != null) {
          gpNodes.add(gparent);
        }
      }
    }

    // ascend nodes
    for (Tree<String> gpNode : gpNodes) {
      if (gpNode.equidepth()) {
        result.add(gpNode.ascend());
      }
    }

    return result;
  }
}
