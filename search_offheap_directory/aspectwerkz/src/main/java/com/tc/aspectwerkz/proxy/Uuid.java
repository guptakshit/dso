/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.aspectwerkz.proxy;

/**
 * NOTE:
 * <p/>
 * This code is based on code from the [Plasmid Replication Engine] project.
 * <br/>
 * Licensed under [Mozilla Public License 1.0 (MPL)].
 * <p/>
 * Original JavaDoc:
 * <p/>
 * Our distributed objects are generally named most efficiently (and cleanly)
 * by their UUID's. This class provides some static helpers for using UUID's.
 * If it was efficient to do in Java, I would make the uuid an normal class
 * and use instances of it. However, in current JVM's, we would end up using an
 * Object to represent a long, which is pretty expensive. Maybe someday. ###
 * <p/>
 * UUID format: currently using currentTimeMillis() for the low bits. This uses
 * about 40 bits for the next 1000 years, leaving 24 bits for debugging
 * and consistency data. I'm using 8 of those for a magic asci 'U' byte.
 * <p/>
 * Future: use one instance of Uuid per type of object for better performance
 * and more detailed info (instance could be matched to its uuid's via a map or
 * array). This all static version bites.###
 */
public final class Uuid {

  public static final long UUID_NONE = 0;
  public static final long UUID_WILD = -1;
  public static final long UUID_MAGICMASK = 0xff << 56;
  public static final long UUID_MAGIC = 'U' << 56;

  protected static long lastTime;

  /**
   * Generate and return a new Universally Unique ID.
   * Happens to be monotonically increasing.
   */
  public synchronized static long newUuid() {
    long time = System.currentTimeMillis();

    if (time <= lastTime) {
      time = lastTime + 1;
    }
    lastTime = time;
    return UUID_MAGIC | time;
  }

  /**
   * Returns true if uuid could have been generated by Uuid.
   */
  public static boolean isValid(final long uuid) {
    return (uuid & UUID_MAGICMASK) == UUID_MAGIC
            && (uuid & ~UUID_MAGICMASK) != 0;
  }
}

