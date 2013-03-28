package com.orientechnologies.orient.core.storage.impl.local.paginated;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.orientechnologies.common.directmemory.ODirectMemory;
import com.orientechnologies.common.directmemory.ODirectMemoryFactory;
import com.orientechnologies.common.util.MersenneTwisterFast;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.config.OStorageClusterConfiguration;
import com.orientechnologies.orient.core.config.OStorageConfiguration;
import com.orientechnologies.orient.core.config.OStorageSegmentConfiguration;
import com.orientechnologies.orient.core.id.OClusterPosition;
import com.orientechnologies.orient.core.id.OClusterPositionFactory;
import com.orientechnologies.orient.core.index.hashindex.local.cache.ODiskCache;
import com.orientechnologies.orient.core.index.hashindex.local.cache.OLRUCache;
import com.orientechnologies.orient.core.storage.ORawBuffer;
import com.orientechnologies.orient.core.storage.impl.local.OStorageLocal;
import com.orientechnologies.orient.core.storage.impl.local.OStorageVariableParser;
import com.orientechnologies.orient.core.version.ORecordVersion;
import com.orientechnologies.orient.core.version.OVersionFactory;

/**
 * @author Andrey Lomakin
 * @since 26.03.13
 */
@Test
public class LocalPaginatedClusterTest {
  public OLocalPaginatedCluster paginatedCluster = new OLocalPaginatedCluster();
  private String                buildDirectory;
  private ODiskCache            diskCache;

  @BeforeClass
  public void beforeClass() throws IOException {
    buildDirectory = System.getProperty("buildDirectory");
    if (buildDirectory == null || buildDirectory.isEmpty())
      buildDirectory = ".";

    buildDirectory += "/localPaginatedClusterTest";

    OStorageLocal storage = mock(OStorageLocal.class);
    OStorageConfiguration storageConfiguration = mock(OStorageConfiguration.class);
    storageConfiguration.clusters = new ArrayList<OStorageClusterConfiguration>();
    storageConfiguration.fileTemplate = new OStorageSegmentConfiguration();

    ODirectMemory directMemory = ODirectMemoryFactory.INSTANCE.directMemory();
    diskCache = new OLRUCache(2L * 1024 * 1024 * 1024, directMemory, OGlobalConfiguration.DISK_CACHE_PAGE_SIZE.getValueAsInteger(),
        storage, false);

    OStorageVariableParser variableParser = new OStorageVariableParser(buildDirectory);

    when(storage.getDiskCache()).thenReturn(diskCache);
    when(storage.getVariableParser()).thenReturn(variableParser);
    when(storage.getConfiguration()).thenReturn(storageConfiguration);
    when(storage.getMode()).thenReturn("rw");

    when(storageConfiguration.getDirectory()).thenReturn(buildDirectory);

    paginatedCluster.configure(storage, 5, "paginatedClusterTest", buildDirectory, -1);
    paginatedCluster.create(-1);
  }

  @AfterClass
  public void afterClass() throws IOException {
    paginatedCluster.delete();
    File file = new File(buildDirectory);
    file.delete();
    diskCache.clear();
  }

  @BeforeMethod
  public void beforeMethod() throws IOException {
    paginatedCluster.truncate();
  }

  public void testAddOneSmallRecord() throws IOException {
    byte[] smallRecord = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    OClusterPosition clusterPosition = paginatedCluster.createRecord(smallRecord, recordVersion, (byte) 1);
    Assert.assertEquals(clusterPosition, OClusterPositionFactory.INSTANCE.valueOf(0));

    ORawBuffer rawBuffer = paginatedCluster.readRecord(clusterPosition);
    Assert.assertNotNull(rawBuffer);

    Assert.assertEquals(rawBuffer.version, recordVersion);
    Assert.assertEquals(rawBuffer.buffer, smallRecord);
    Assert.assertEquals(rawBuffer.recordType, 1);
  }

  public void testAddOneBigRecord() throws IOException {
    byte[] bigRecord = new byte[2 * 65536 + 100];
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast();
    mersenneTwisterFast.nextBytes(bigRecord);

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 1);
    Assert.assertEquals(clusterPosition, OClusterPositionFactory.INSTANCE.valueOf(0));

    ORawBuffer rawBuffer = paginatedCluster.readRecord(clusterPosition);
    Assert.assertNotNull(rawBuffer);

    Assert.assertEquals(rawBuffer.version, recordVersion);
    Assert.assertEquals(rawBuffer.buffer, bigRecord);
    Assert.assertEquals(rawBuffer.recordType, 1);
  }

  public void testAddManySmallRecords() throws IOException {
    final int records = 10000;

    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);
    System.out.println("testAddManySmallRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(OLocalPage.MAX_RECORD_SIZE - 1) + 1;
      byte[] smallRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(smallRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(smallRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, smallRecord);
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testAddManyBigRecords() throws IOException {
    final int records = 10000;

    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testAddManyBigRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(2 * OLocalPage.MAX_RECORD_SIZE) + OLocalPage.MAX_RECORD_SIZE + 1;
      byte[] bigRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(bigRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, bigRecord);
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testAddManyRecords() throws IOException {
    final int records = 10000;
    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testAddManyRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(2 * OLocalPage.MAX_RECORD_SIZE) + 1;
      byte[] smallRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(smallRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(smallRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, smallRecord);
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testRemoveHalfSmallRecords() throws IOException {
    final int records = 10000;
    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testRemoveHalfSmallRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(OLocalPage.MAX_RECORD_SIZE - 1) + 1;
      byte[] smallRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(smallRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(smallRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, smallRecord);
    }

    int deletedRecords = 0;
    Assert.assertEquals(records, paginatedCluster.getEntries());
    Set<OClusterPosition> deletedPositions = new HashSet<OClusterPosition>();
    Iterator<OClusterPosition> positionIterator = positionRecordMap.keySet().iterator();
    while (positionIterator.hasNext()) {
      OClusterPosition clusterPosition = positionIterator.next();
      if (mersenneTwisterFast.nextBoolean()) {
        deletedPositions.add(clusterPosition);
        Assert.assertTrue(paginatedCluster.deleteRecord(clusterPosition));
        deletedRecords++;

        Assert.assertEquals(records - deletedRecords, paginatedCluster.getEntries());

        positionIterator.remove();
      }
    }

    Assert.assertEquals(paginatedCluster.getEntries(), records - deletedRecords);
    for (OClusterPosition deletedPosition : deletedPositions) {
      Assert.assertNull(paginatedCluster.readRecord(deletedPosition));
      Assert.assertFalse(paginatedCluster.deleteRecord(deletedPosition));
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testRemoveHalfBigRecords() throws IOException {
    final int records = 10000;
    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testRemoveHalfBigRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(2 * OLocalPage.MAX_RECORD_SIZE) + OLocalPage.MAX_RECORD_SIZE + 1;

      byte[] bigRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(bigRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, bigRecord);
    }

    int deletedRecords = 0;
    Assert.assertEquals(records, paginatedCluster.getEntries());
    Set<OClusterPosition> deletedPositions = new HashSet<OClusterPosition>();
    Iterator<OClusterPosition> positionIterator = positionRecordMap.keySet().iterator();
    while (positionIterator.hasNext()) {
      OClusterPosition clusterPosition = positionIterator.next();
      if (mersenneTwisterFast.nextBoolean()) {
        deletedPositions.add(clusterPosition);
        Assert.assertTrue(paginatedCluster.deleteRecord(clusterPosition));
        deletedRecords++;

        Assert.assertEquals(records - deletedRecords, paginatedCluster.getEntries());

        positionIterator.remove();
      }
    }

    Assert.assertEquals(paginatedCluster.getEntries(), records - deletedRecords);
    for (OClusterPosition deletedPosition : deletedPositions) {
      Assert.assertNull(paginatedCluster.readRecord(deletedPosition));
      Assert.assertFalse(paginatedCluster.deleteRecord(deletedPosition));
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testRemoveHalfRecords() throws IOException {
    final int records = 10000;
    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testRemoveHalfRecords seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(3 * OLocalPage.MAX_RECORD_SIZE) + 1;

      byte[] bigRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(bigRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, bigRecord);
    }

    int deletedRecords = 0;
    Assert.assertEquals(records, paginatedCluster.getEntries());
    Set<OClusterPosition> deletedPositions = new HashSet<OClusterPosition>();
    Iterator<OClusterPosition> positionIterator = positionRecordMap.keySet().iterator();
    while (positionIterator.hasNext()) {
      OClusterPosition clusterPosition = positionIterator.next();
      if (mersenneTwisterFast.nextBoolean()) {
        deletedPositions.add(clusterPosition);
        Assert.assertTrue(paginatedCluster.deleteRecord(clusterPosition));
        deletedRecords++;

        Assert.assertEquals(records - deletedRecords, paginatedCluster.getEntries());

        positionIterator.remove();
      }
    }

    Assert.assertEquals(paginatedCluster.getEntries(), records - deletedRecords);
    for (OClusterPosition deletedPosition : deletedPositions) {
      Assert.assertNull(paginatedCluster.readRecord(deletedPosition));
      Assert.assertFalse(paginatedCluster.deleteRecord(deletedPosition));
    }

    for (Map.Entry<OClusterPosition, byte[]> entry : positionRecordMap.entrySet()) {
      ORawBuffer rawBuffer = paginatedCluster.readRecord(entry.getKey());
      Assert.assertNotNull(rawBuffer);

      Assert.assertEquals(rawBuffer.version, recordVersion);
      Assert.assertEquals(rawBuffer.buffer, entry.getValue());
      Assert.assertEquals(rawBuffer.recordType, 2);
    }
  }

  public void testRemoveHalfRecordsAndAddAnotherHalfAgain() throws IOException {
    final int records = 10000;
    long seed = System.currentTimeMillis();
    MersenneTwisterFast mersenneTwisterFast = new MersenneTwisterFast(seed);

    System.out.println("testRemoveHalfRecordsAndAddAnotherHalfAgain seed : " + seed);

    Map<OClusterPosition, byte[]> positionRecordMap = new HashMap<OClusterPosition, byte[]>();

    ORecordVersion recordVersion = OVersionFactory.instance().createVersion();
    recordVersion.increment();
    recordVersion.increment();

    for (int i = 0; i < records; i++) {
      int recordSize = mersenneTwisterFast.nextInt(3 * OLocalPage.MAX_RECORD_SIZE) + 1;

      byte[] bigRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(bigRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, bigRecord);
    }

    int deletedRecords = 0;
    Assert.assertEquals(records, paginatedCluster.getEntries());

    Iterator<OClusterPosition> positionIterator = positionRecordMap.keySet().iterator();
    while (positionIterator.hasNext()) {
      OClusterPosition clusterPosition = positionIterator.next();
      if (mersenneTwisterFast.nextBoolean()) {
        Assert.assertTrue(paginatedCluster.deleteRecord(clusterPosition));
        deletedRecords++;

        Assert.assertEquals(paginatedCluster.getEntries(), records - deletedRecords);

        positionIterator.remove();
      }
    }

    Assert.assertEquals(paginatedCluster.getEntries(), records - deletedRecords);

    for (int i = 0; i < records / 2; i++) {
      int recordSize = mersenneTwisterFast.nextInt(3 * OLocalPage.MAX_RECORD_SIZE) + 1;

      byte[] bigRecord = new byte[recordSize];
      mersenneTwisterFast.nextBytes(bigRecord);

      final OClusterPosition clusterPosition = paginatedCluster.createRecord(bigRecord, recordVersion, (byte) 2);

      positionRecordMap.put(clusterPosition, bigRecord);
    }

    Assert.assertEquals(paginatedCluster.getEntries(), (long) (1.5 * records - deletedRecords));
  }
}