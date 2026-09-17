package com.thedesitadka.app.storage;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DownloadDao_Impl implements DownloadDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DownloadRecordEntity> __insertionAdapterOfDownloadRecordEntity;

  private final EntityDeletionOrUpdateAdapter<DownloadRecordEntity> __updateAdapterOfDownloadRecordEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDownload;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCompleted;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public DownloadDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDownloadRecordEntity = new EntityInsertionAdapter<DownloadRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `downloads` (`id`,`contentId`,`providerId`,`title`,`thumbnailUrl`,`mediaUrl`,`localFilePath`,`mimeType`,`totalBytes`,`downloadedBytes`,`progress`,`speedBytesPerSec`,`etaSeconds`,`status`,`error`,`retryCount`,`createdAt`,`completedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadRecordEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getContentId());
        statement.bindString(3, entity.getProviderId());
        statement.bindString(4, entity.getTitle());
        statement.bindString(5, entity.getThumbnailUrl());
        statement.bindString(6, entity.getMediaUrl());
        statement.bindString(7, entity.getLocalFilePath());
        statement.bindString(8, entity.getMimeType());
        statement.bindLong(9, entity.getTotalBytes());
        statement.bindLong(10, entity.getDownloadedBytes());
        statement.bindLong(11, entity.getProgress());
        statement.bindLong(12, entity.getSpeedBytesPerSec());
        statement.bindLong(13, entity.getEtaSeconds());
        statement.bindString(14, __DownloadStatus_enumToString(entity.getStatus()));
        if (entity.getError() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getError());
        }
        statement.bindLong(16, entity.getRetryCount());
        statement.bindLong(17, entity.getCreatedAt());
        if (entity.getCompletedAt() == null) {
          statement.bindNull(18);
        } else {
          statement.bindLong(18, entity.getCompletedAt());
        }
      }
    };
    this.__updateAdapterOfDownloadRecordEntity = new EntityDeletionOrUpdateAdapter<DownloadRecordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `downloads` SET `id` = ?,`contentId` = ?,`providerId` = ?,`title` = ?,`thumbnailUrl` = ?,`mediaUrl` = ?,`localFilePath` = ?,`mimeType` = ?,`totalBytes` = ?,`downloadedBytes` = ?,`progress` = ?,`speedBytesPerSec` = ?,`etaSeconds` = ?,`status` = ?,`error` = ?,`retryCount` = ?,`createdAt` = ?,`completedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadRecordEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getContentId());
        statement.bindString(3, entity.getProviderId());
        statement.bindString(4, entity.getTitle());
        statement.bindString(5, entity.getThumbnailUrl());
        statement.bindString(6, entity.getMediaUrl());
        statement.bindString(7, entity.getLocalFilePath());
        statement.bindString(8, entity.getMimeType());
        statement.bindLong(9, entity.getTotalBytes());
        statement.bindLong(10, entity.getDownloadedBytes());
        statement.bindLong(11, entity.getProgress());
        statement.bindLong(12, entity.getSpeedBytesPerSec());
        statement.bindLong(13, entity.getEtaSeconds());
        statement.bindString(14, __DownloadStatus_enumToString(entity.getStatus()));
        if (entity.getError() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getError());
        }
        statement.bindLong(16, entity.getRetryCount());
        statement.bindLong(17, entity.getCreatedAt());
        if (entity.getCompletedAt() == null) {
          statement.bindNull(18);
        } else {
          statement.bindLong(18, entity.getCompletedAt());
        }
        statement.bindString(19, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteDownload = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM downloads WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteCompleted = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM downloads WHERE status = 'COMPLETED'";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM downloads";
        return _query;
      }
    };
  }

  @Override
  public Object insertDownload(final DownloadRecordEntity item,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDownloadRecordEntity.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateDownload(final DownloadRecordEntity item,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfDownloadRecordEntity.handle(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteDownload(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteDownload.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteDownload.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCompleted(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCompleted.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteCompleted.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DownloadRecordEntity>> getAllDownloads() {
    final String _sql = "SELECT * FROM downloads WHERE status != 'DELETED' ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"downloads"}, new Callable<List<DownloadRecordEntity>>() {
      @Override
      @NonNull
      public List<DownloadRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<DownloadRecordEntity> _result = new ArrayList<DownloadRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecordEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<DownloadRecordEntity>> getDownloadsByStatus(final DownloadStatus status) {
    final String _sql = "SELECT * FROM downloads WHERE status = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, __DownloadStatus_enumToString(status));
    return CoroutinesRoom.createFlow(__db, false, new String[] {"downloads"}, new Callable<List<DownloadRecordEntity>>() {
      @Override
      @NonNull
      public List<DownloadRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<DownloadRecordEntity> _result = new ArrayList<DownloadRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecordEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getDownload(final String id,
      final Continuation<? super DownloadRecordEntity> $completion) {
    final String _sql = "SELECT * FROM downloads WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DownloadRecordEntity>() {
      @Override
      @Nullable
      public DownloadRecordEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final DownloadRecordEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _result = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getFailedDownloads(
      final Continuation<? super List<DownloadRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM downloads WHERE status = 'FAILED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DownloadRecordEntity>>() {
      @Override
      @NonNull
      public List<DownloadRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<DownloadRecordEntity> _result = new ArrayList<DownloadRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecordEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getInterruptedDownloads(
      final Continuation<? super List<DownloadRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM downloads WHERE status = 'DOWNLOADING'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DownloadRecordEntity>>() {
      @Override
      @NonNull
      public List<DownloadRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<DownloadRecordEntity> _result = new ArrayList<DownloadRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecordEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllDownloadsList(
      final Continuation<? super List<DownloadRecordEntity>> $completion) {
    final String _sql = "SELECT * FROM downloads";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DownloadRecordEntity>>() {
      @Override
      @NonNull
      public List<DownloadRecordEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContentId = CursorUtil.getColumnIndexOrThrow(_cursor, "contentId");
          final int _cursorIndexOfProviderId = CursorUtil.getColumnIndexOrThrow(_cursor, "providerId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfThumbnailUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailUrl");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaUrl");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDownloadedBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedBytes");
          final int _cursorIndexOfProgress = CursorUtil.getColumnIndexOrThrow(_cursor, "progress");
          final int _cursorIndexOfSpeedBytesPerSec = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBytesPerSec");
          final int _cursorIndexOfEtaSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "etaSeconds");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<DownloadRecordEntity> _result = new ArrayList<DownloadRecordEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadRecordEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContentId;
            _tmpContentId = _cursor.getString(_cursorIndexOfContentId);
            final String _tmpProviderId;
            _tmpProviderId = _cursor.getString(_cursorIndexOfProviderId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpThumbnailUrl;
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
            final String _tmpMediaUrl;
            _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            final String _tmpLocalFilePath;
            _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDownloadedBytes;
            _tmpDownloadedBytes = _cursor.getLong(_cursorIndexOfDownloadedBytes);
            final int _tmpProgress;
            _tmpProgress = _cursor.getInt(_cursorIndexOfProgress);
            final long _tmpSpeedBytesPerSec;
            _tmpSpeedBytesPerSec = _cursor.getLong(_cursorIndexOfSpeedBytesPerSec);
            final long _tmpEtaSeconds;
            _tmpEtaSeconds = _cursor.getLong(_cursorIndexOfEtaSeconds);
            final DownloadStatus _tmpStatus;
            _tmpStatus = __DownloadStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new DownloadRecordEntity(_tmpId,_tmpContentId,_tmpProviderId,_tmpTitle,_tmpThumbnailUrl,_tmpMediaUrl,_tmpLocalFilePath,_tmpMimeType,_tmpTotalBytes,_tmpDownloadedBytes,_tmpProgress,_tmpSpeedBytesPerSec,_tmpEtaSeconds,_tmpStatus,_tmpError,_tmpRetryCount,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __DownloadStatus_enumToString(@NonNull final DownloadStatus _value) {
    switch (_value) {
      case QUEUED: return "QUEUED";
      case DOWNLOADING: return "DOWNLOADING";
      case PAUSED: return "PAUSED";
      case RETRYING: return "RETRYING";
      case COMPLETED: return "COMPLETED";
      case FAILED: return "FAILED";
      case CANCELLED: return "CANCELLED";
      case DELETED: return "DELETED";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private DownloadStatus __DownloadStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "QUEUED": return DownloadStatus.QUEUED;
      case "DOWNLOADING": return DownloadStatus.DOWNLOADING;
      case "PAUSED": return DownloadStatus.PAUSED;
      case "RETRYING": return DownloadStatus.RETRYING;
      case "COMPLETED": return DownloadStatus.COMPLETED;
      case "FAILED": return DownloadStatus.FAILED;
      case "CANCELLED": return DownloadStatus.CANCELLED;
      case "DELETED": return DownloadStatus.DELETED;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
