/*
    Copyright 2013 Semantic Discovery, Inc. (www.semanticdiscovery.com)

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
package org.sd.util.attribute;


import java.util.List;

/**
 * Container for associating an attribute with a value (or values).
 * <p>
 * Templated for the canonical attribute enumeration, E, and the value object, V.
 * <p>
 * Note that Un-canonized (or free) attributes use the "otherType" to express
 * the attribute.
 *
 * @author Spence Koehler
 */
public class AttValPair <E extends Canonical, V, M> extends AbstractAmbiguousEntity<AttValPair<E, V, M>> implements Canonical, MultipleValues<V> {
  
  // AttValPair fields
  private E attType;
  private String otherType;
  private MultipleValuesContainer<V> values;
  private M metaData;
  private AvpContainer<E, V, M> container;

  /**
   * Create with a canonical attribute.
   */
  public AttValPair(E attType, V value) {
    init();
    this.attType = attType;
    this.values = new MultipleValuesContainer<V>(value);
    this.otherType = (attType == null) ? null : attType.toString();
    this.container = null;
  }

  /**
   * Create with a free (non-canonical) attribute.
   */
  public AttValPair(String otherType, V value) {
    init();
    this.attType = null;
    this.values = new MultipleValuesContainer<V>(value);
    this.otherType = otherType;
    this.container = null;
  }

  /**
   * Create with given field values.
   */
  public AttValPair(E attType, String otherType, V value) {
    init();
    this.attType = attType;
    this.otherType = otherType;
    this.values = new MultipleValuesContainer<V>(value);
    this.container = null;
  }

  /**
   * Copy constructor (shallow).
   * <p>
   * Note that this creates a copy of just the attribute and not its ambiguity
   * chains.
   * <p>
   * If makeNewValues, then multiple values will be copied into a new container;
   * otherwise, the same multiple values container will be shared in the new
   * instance.
   */
  public AttValPair(AttValPair<E, V, M> other, boolean makeNewValues) {
    init();
    this.attType = other.attType;
    this.otherType = other.otherType;
    this.values = makeNewValues ? new MultipleValuesContainer<V>(other.values) : other.values;
    this.metaData = other.metaData;
    this.container = other.container;
  }

  /** Set this instance's container. Maintained thru AvpContainer. */
  void setContainer(AvpContainer<E, V, M> container) {
    this.container = container;
  }

  void clearAllContainers() {
    for (AttValPair<E, V, M> avp = this; avp != null; avp = avp.nextAmbiguity()) {
      avp.container = null;
    }
  }

  public AvpContainer<E, V, M> getContainer() {
    return container;
  }

  public String toString() {
    final StringBuilder result = new StringBuilder();

    if (attType != null) {
      result.append(attType);
    }
    else if (otherType != null && !"".equals(otherType)) {
      result.append('%').append(otherType);
    }
    else {
      result.append('?');
    }

    if (values != null) {
      result.append('=').append(values.toString());
    }

    if (metaData != null) {
      // add a marker indicating the presence of metadata
      result.append('+');
    }

    final AttValPair<E, V, M> nextAmbiguity = nextAmbiguity();
    if (nextAmbiguity != null) {
      result.append('|').append(nextAmbiguity.toString());
    }

    return result.toString();
  }

  /** Initialize all instance fields */
  private final void init() {
    this.attType = null;
    this.otherType = null;
    this.values = null;
    this.metaData = null;
  }

  /** Determine whether this instance holds meta-data. */
  public boolean hasMetaData() {
    return metaData != null;
  }

  /** Get the meta-data associated with this instance. */
  public M getMetaData() {
    return metaData;
  }

  /** Set the meta-data for this instance. */
  public M setMetaData(M metaData) {
    M result = this.metaData;
    this.metaData = metaData;
    return result;
  }

  /** Get the (canonical) attribute type. */
  public E getAttType() {
    return attType;
  }

  /**
   * Set the (canonical) attribute type for this instance.
   */
  public void setAttType(E attType) {
    this.attType = attType;
  }

  /** Determine whether this instance has a non-empty other type. */
  public boolean hasOtherType() {
    return otherType != null && !"".equals(otherType);
  }

  /** Get the other (non-canonical) type. */
  public String getOtherType() {
    return otherType;
  }

  /** Get the value. */
  public V getValue() {
    return values == null ? null : values.getValue();
  }

  /** Set the value, overwriting any existing value or values. */
  public void setValue(V value) {
    if (this.values == null) {
      this.values = new MultipleValuesContainer<V>(value);
    }
    else {
      this.values.setValue(value);
    }
  }

  /** Simple typecasting helper auxiliary for getting the next ambiguity. */
  public AttValPair<E, V, M> nextAmbiguity() {
    return (AttValPair<E, V, M>)getNextAmbiguity();
  }

  /** Simple typecasting helper auxiliary for getting the first ambiguity. */
  public AttValPair<E, V, M> firstAmbiguity() {
    return (AttValPair<E, V, M>)getFirstAmbiguity();
  }

  /**
   * Create a copy of this instance with its ambiguity chain, adding as
   * additional ambiguities to result if result is non-null.
   */
  public AttValPair<E, V, M> copyAmbiguityChain(AttValPair<E, V, M> result) {
    AttValPair<E, V, M> avp = this;

    if (result == null) {
      result = new AttValPair<E, V, M>(this, true);
      avp = avp.nextAmbiguity();
    }
    
    while (avp != null) {
      result.addAmbiguity(new AttValPair<E, V, M>(avp, false));
      avp = avp.nextAmbiguity();
    }

    return result;
  }


  ////////
  ///
  /// Implement Canonical interface
  ///

  /** Determine whether this holds a canonical attribute. */
  public boolean isCanonical() {
    return (attType == null) ? false : attType.isCanonical();
  }

  ///
  /// end of Canonical interface implementation
  ///
  ////////

  ////////
  ///
  /// Implement AmbiguousEntity interface
  ///

  /**
   * Safely and efficiently typecast this to an AttValPair.
   */
  public AttValPair<E, V, M> getEntity() {
    return this;
  }

  /**
   * Override to update this instance's container.
   */
  public void discard() {
    if (container != null) {
      container.remove(this);
    }
    super.discard();
  }

  /**
   * Override to update this instance's container.
   */
  public void resolve() {
    final AvpContainer<E, V, M> theContainer = this.container;
    if (container != null) {
      container.removeAll(this);  //note: this unsets container for this instance
    }
    super.resolve();
    if (theContainer != null) {
      theContainer.add(this);  // restores container
    }
  }

  /** Determine whether this ambiguous entity matches (is a duplicate of) the other */
  public boolean matches(AmbiguousEntity<AttValPair<E, V, M>> other) {
    boolean result = (this == other);
    if (!result && other != null) {
      // types must match
      final AttValPair<E, V, M> otherAVP = other.getEntity();
      if (this.attType == null && otherAVP.attType == null) {
        // rely on otherType
        result = ((this.otherType == otherAVP.otherType) ||
                  (this.otherType != null && this.otherType.equals(otherAVP.otherType)));
      }
      else {
        // rely on attType
        result = (this.attType == otherAVP.attType);
      }

      // and values must match
      if (result) {
        result = this.values.equals(otherAVP.values);
      }
    }

    return result;
  }

  ///
  /// end of AmbiguousEntity interface implementation
  ///
  ////////

  ////////
  ///
  /// Implement MultipleValues interface
  ///

  /**
   * Safely and efficiently typecast this to an MultipleValues.
   */
  public MultipleValues<V> asMultipleValues() {
    return this;
  }

  /** Determine whether the current instance has multiple values. */
  public boolean hasMultipleValues() {
    return values != null && values.hasMultipleValues();
  }

  /** Get all of this instance's values. */
  public List<V> getValues() {
    return values == null ? null : values.getValues();
  }

  /** Get the number of values. */
  public int getValuesCount() {
    return values == null ? 0 : values.getValuesCount();
  }

  /** Add another value, returning true if added (unique). */
  public boolean addValue(V value) {
    boolean result = false;

    if (value != null) {
      if (values == null) {
        values = new MultipleValuesContainer<V>(value);
        result = true;
      }
      else {
        result = values.addValue(value);
      }
    }

    return result;
  }

  /** Remove the given value, returning true if removed (existed). */
  public boolean removeValue(V value) {
    return (values == null) ? false : values.removeValue(value);
  }
  

  ///
  /// end of MultipleValues interface implementation
  ///
  ////////
}
