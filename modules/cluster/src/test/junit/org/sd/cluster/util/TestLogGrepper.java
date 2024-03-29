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
package org.sd.cluster.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.sd.util.DateUtil;

/**
 * JUnit Tests for the LogGrepper class.
 * <p>
 * @author Spence Koehler
 */
public class TestLogGrepper extends TestCase {

  public TestLogGrepper(String name) {
    super(name);
  }
  
  public void testShouldGrep1() {
    final Integer[] ymdhms = DateUtil.parseDateComponents("2007-02-23-21:14");
    final LogGrepper grepper = new LogGrepper("test pattern", ymdhms, false, true, false, true, true, false, false, null);

    assertTrue(grepper.shouldGrep("log-2007-02-23-21:15:33-1.err"));
  }

  public static Test suite() {
    TestSuite suite = new TestSuite(TestLogGrepper.class);
    return suite;
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
