/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright notice.  All rights reserved.
 */
package com.tc.statistics.buffer.exceptions;

import com.tc.statistics.StatisticData;

public class TCStatisticsBufferStatisticStorageErrorException extends TCStatisticsBufferException {
  private final Long sessionId;
  private final StatisticData data;

  public TCStatisticsBufferStatisticStorageErrorException(final long sessionId, final StatisticData data) {
    super("Unexpected error while storing the statistic with id '" + sessionId + "' and data " + data + ".", null);
    this.sessionId = new Long(sessionId);
    this.data = data;
  }

  public TCStatisticsBufferStatisticStorageErrorException(final StatisticData data, final Throwable cause) {
    super("Unexpected error while storing the statistic data " + data + ".", cause);
    this.sessionId = null;
    this.data = data;
  }

  public Long getSessionId() {
    return sessionId;
  }

  public StatisticData getData() {
    return data;
  }
}