/*
    Copyright 2009 Semantic Discovery, Inc. (www.semanticdiscovery.com)

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


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sd.cio.MessageHelper;
import org.sd.io.PersistablePublishable;
import org.sd.util.tree.Tree;
import org.sd.xml.XmlLite;
import org.sd.xml.XmlReconstructor;

/**
 * Container and organizer for related ExtractionContainer instances (e.g. all
 * from the same underlying source page.)
 * <p>
 * @author Spence Koehler
 */
public class ExtractionGroups extends PersistablePublishable {
  
  //
  //NOTE: If member variables are changed here or in subclasses, update
  //      CURRENT_VERSION and keep track of deserializing old persisted
  //      instances!
  //
  private static final int CURRENT_VERSION = 1;


  // all extractions in document order.
  private List<ExtractionContainer> extractions;

  // all extractions mapped (in document order) by key
  private Map<String, List<ExtractionContainer>> _key2extractions;

  // reconstructor for the tree of all extraction keys
  private XmlReconstructor _xmlReconstructor;

  // all partitioned groups of extractions
  private List<ExtractionGroup> _groups;


  /**
   * Empty constructor for publishable reconstruction.
   */
  public ExtractionGroups() {
  }

  /**
   * Construct with the parses in the given parse output collector.
   */
  public ExtractionGroups(ParseOutputCollector output) {
    this.extractions = loadExtractions(output);
    this._key2extractions = null;
    this._xmlReconstructor = null;
    this._groups = null;
  }

  /**
   * Return all extractions in order.
   */
  public List<ExtractionContainer> getExtractions() {
    return extractions;
  }

  /**
   * Get all partitioned groups of extractions.
   */
  public List<ExtractionGroup> getExtractionGroups() {
    if (_groups == null) {
      _groups = partitionExtractions();
    }
    return _groups;
  }

  /**
   * Get the the number of extraction groups.
   */
  public int getNumGroups() {
    final List<ExtractionGroup> groups = getExtractionGroups();
    return groups.size();
  }

  /**
   * Get all extractions mapped (in document order) by key.
   */
  public Map<String, List<ExtractionContainer>> getKey2Extractions() {
    if (_key2extractions == null) {
      createKey2Extractions();
    }
    return _key2extractions;
  }

  /**
   * Get the xmlReconstructor for all extractions' paths.
   */
  public XmlReconstructor getXmlReconstructor() {
    if (_xmlReconstructor == null) {
      createKey2Extractions();
    }
    return _xmlReconstructor;
  }

  /**
   * Remove the extraction from this instance.
   */
  public boolean removeExtractionContainer(ExtractionContainer extraction) {

    final boolean result = this.extractions.remove(extraction);

    if (result) {
      if (_key2extractions != null) {
        final String key = extraction.getKey();
        final List<ExtractionContainer> extractions = _key2extractions.get(key);
        if (extractions != null) {
          extractions.remove(extraction);
        }

        if (extractions == null || extractions.size() == 0) {
          _key2extractions.remove(key);
        }
      }

      if (_xmlReconstructor != null) {
        final Tree<XmlLite.Data> terminalNode = extraction.getTerminalNode();
        if (terminalNode != null) {
          _xmlReconstructor.removeTagPath(terminalNode);
        }
      }

      this._groups = null;  // reset for recompute
    }

    return result;
  }


  ////////
  //
  // PersistablePublishable interface implementation

  /**
   * Get the current version.
   * <p>
   * Note that changes to subclasses as well as to this class will require
   * this value to change and proper handling in the write/read methods.
   */
  protected final int getCurrentVersion() {
    return CURRENT_VERSION;
  }

  /**
   * Write this message to the dataOutput stream such that this message
   * can be completely reconstructed through this.read(dataInput).
   *
   * @param dataOutput  the data output to write to.
   */
  protected void writeCurrentVersion(DataOutput dataOutput) throws IOException {
    dataOutput.writeInt(extractions.size());
    for (ExtractionContainer extraction : extractions) {
      MessageHelper.writePublishable(dataOutput, extraction);
    }
  }

  /**
   * Read this message's contents from the dataInput stream that was written by
   * this.write(dataOutput).
   * <p>
   * NOTE: this requires all implementing classes to have a default constructor
   *       with no args.
   *
   * @param dataInput  the data output to write to.
   */
  protected void readVersion(int version, DataInput dataInput) throws IOException {
    if (version == 1) {
      readVersion1(dataInput);
    }
    else {
      badVersion(version);
    }
  }

  private final void readVersion1(DataInput dataInput) throws IOException {
    this.extractions = new ArrayList<ExtractionContainer>();

    final int numExtractions = dataInput.readInt();
    for (int extNum = 0; extNum < numExtractions; ++extNum) {
      final ExtractionContainer extraction = (ExtractionContainer)MessageHelper.readPublishable(dataInput);
      extractions.add(extraction);
    }
  }

  //
  // end of PersistablePublishable interface implementation
  //
  ////////


  /**
   * Partition the extractions into groups (overridable).
   */
  protected List<ExtractionGroup> partitionExtractions() {
    List<ExtractionGroup> result = new ArrayList<ExtractionGroup>();

    if (extractions == null || extractions.size() == 0) return result;

    // Get xmlReconstructor so we can use xml structure here.
    // NOTE: side-effect places reconstructed nodes into extraction container instances.
    final XmlReconstructor xmlReconstructor = getXmlReconstructor();

    final Tree<XmlLite.Data> keyTree = xmlReconstructor.getXmlTree();

    // Algorithm:
    // - Put first extraction into first group, set as "prior"
    // - Loop (while next != null)
    //   - Consider (next extraction with prior extraction = np) and (next extraction with following extraction = nf)
    //     - if f == null, then add to current group
    //       - note that this means that exactly 2 extractions will always be grouped together
    //       - ?maybe include a "maximum distance" constraint measured either in characters and/or in tree traversal distance?
    //     - if np.dca (deepest common ancestor) == nf.dca, then add next extraction to the current group
    //     - else, np.dca != nf.dca
    //       - if np.dca is ancestor of nf.dca, then next extraction starts a new group
    //       - if np.dca is descendant of nf.dca, then next extraction goes into current group as its last member
    //   - increment: prior = next, next = following

    ExtractionContainer prior = null;
    ExtractionGroup curGroup = null;
    ExtractionContainer next = null;

    for (ExtractionContainer following : extractions) {
      if (next == null) {
        // still walking up to where we've got prior, next, and following
        if (prior == null) {
          // this is the first extraction
          prior = following;

          curGroup = new ExtractionGroup();
          result.add(curGroup);
          curGroup.add(prior);

          // don't want next = following = prior.
          following = null;
        }
        else {
          // else this is the second extraction and'll become the first 'next' (unless there's ambiguity)

          final boolean ambiguity = prior.getGlobalStartPosition() == following.getGlobalStartPosition();
          if (ambiguity) {
            curGroup.add(following);
            following = null;
          }
        }
      }
      else {
        // have prior, next, and following
        if (curGroup == null) {
          // prior group has been closed, so 'next' goes into a new group
          curGroup = new ExtractionGroup();
          result.add(curGroup);
          curGroup.add(next);
        }
        else {
          if ("Esther Kaplan".equals(next.getLocalText())) {
            final boolean stopHere = true;
          }

          final boolean ambiguity = next.getGlobalStartPosition() == following.getGlobalStartPosition();
          if (ambiguity) {
            curGroup.add(next);
            next = prior;  // don't change 'prior' on increment
          }
          else {
            final Tree<XmlLite.Data> nextNode = next.getTerminalNode();

            // next/prior deepest common ancestor node
            final Tree<XmlLite.Data> npNode = nextNode.getDeepestCommonAncestor(prior.getTerminalNode());

            // next/following deepest common ancestor node
            final Tree<XmlLite.Data> nfNode = nextNode.getDeepestCommonAncestor(following.getTerminalNode());

            if (npNode == nfNode) {
              // keep in the same group
              curGroup.add(next);
            }
            else {
              if (npNode.isAncestor(nfNode, true)) {
                // start a new group
                curGroup = new ExtractionGroup();
                result.add(curGroup);
                curGroup.add(next);
              }
              else {  // nfNode.isAncestor(npNode)
                // add as last member of current group
                curGroup.add(next);
                if (!ambiguity) curGroup = null;  // close group
              }
            }
          }
        }
        
        // increment prior
        prior = next;
      }

      // set 'next' before incrementing 'following'
      next = following;
    }

    // Handle last 'next' (has no 'following')
    if (next != null) {
      if (curGroup == null) {
        curGroup = new ExtractionGroup();
        result.add(curGroup);
      }
      curGroup.add(next);
    }

    return result;
  }

  private final List<ExtractionContainer> loadExtractions(ParseOutputCollector output) {
    final List<ExtractionContainer> result = new ArrayList<ExtractionContainer>();

    final List<AtnParseResult> parseResults = output.getParseResults();
    for (AtnParseResult parseResult : parseResults) {
      final ExtractionContainer extraction = ExtractionContainer.createExtractionContainer(parseResult);
      if (extraction != null) {
        result.add(extraction);
      }
    }

    return result;
  }

  /**
   * Extract parses from the output as ExtractionContainers.
   */
  private final void createKey2Extractions() {
    this._key2extractions = new LinkedHashMap<String, List<ExtractionContainer>>();
    this._xmlReconstructor = new XmlReconstructor();

    for (ExtractionContainer extraction : this.extractions) {
      final String key = extraction.getKey();
      List<ExtractionContainer> extractions = _key2extractions.get(key);
      if (extractions == null) {
        extractions = new ArrayList<ExtractionContainer>();
        _key2extractions.put(key, extractions);
      }
      extractions.add(extraction);
      Collections.sort(extractions);

      final Tree<XmlLite.Data> terminalNode = _xmlReconstructor.addTagPath(key, extraction.getLocalText());
      extraction.setTerminalNode(terminalNode);
    }
  }
}