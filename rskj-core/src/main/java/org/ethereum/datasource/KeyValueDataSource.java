/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public interface KeyValueDataSource extends DataSource {
    String DB_KIND_PROPERTIES_FILE = "dbKind.properties";
    String KEYVALUE_DATASOURCE_PROP_NAME = "keyvalue.datasource";
    String KEYVALUE_DATASOURCE = "KeyValueDataSource";

    @Nullable
    byte[] get(byte[] key);

    /**
     * null puts() are NOT allowed.
     *
     * @return the same value it received
     */
    byte[] put(byte[] key, byte[] value);

    void delete(byte[] key);

    Set<ByteArrayWrapper> keys();

    DataSourceKeyIterator keyIterator();

    /**
     * Note that updateBatch() does not imply the operation is atomic:
     * if somethings breaks, it's possible that some keys get written and some
     * others don't.
     * IMPORTANT: keysToRemove override entriesToUpdate
     *
     * @param entriesToUpdate
     * @param keysToRemove
     */
    void updateBatch(Map<ByteArrayWrapper, byte[]> entriesToUpdate, Set<ByteArrayWrapper> keysToRemove);

    /**
     * This makes things go to disk. To enable caching.
     */
    void flush();

    @Nonnull
    static KeyValueDataSource makeDataSource(@Nonnull Path datasourcePath, @Nonnull DbKind kind) {
        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        KeyValueDataSource ds;
        switch (kind) {
            case LEVEL_DB:
                ds = new LevelDbDataSource(name, databaseDir);
                break;
            case ROCKS_DB:
                ds = new RocksDbDataSource(name, databaseDir);
                break;
            default:
                throw new IllegalArgumentException("kind");
        }

        ds.init();

        return ds;
    }

    static void mergeDataSources(@Nonnull Path destinationPath, @Nonnull List<Path> originPaths, @Nonnull DbKind kind) {
        Map<ByteArrayWrapper, byte[]> mergedStores = new HashMap<>();
        for (Path originPath : originPaths) {
            KeyValueDataSource singleOriginDataSource = makeDataSource(originPath, kind);
            for (ByteArrayWrapper byteArrayWrapper : singleOriginDataSource.keys()) {
                mergedStores.put(byteArrayWrapper, singleOriginDataSource.get(byteArrayWrapper.getData()));
            }
            singleOriginDataSource.close();
        }
        KeyValueDataSource destinationDataSource = makeDataSource(destinationPath, kind);
        destinationDataSource.updateBatch(mergedStores, Collections.emptySet());
        destinationDataSource.close();
    }

    static DbKind getDbKindValueFromDbKindFile(String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();

            if (file.exists() && file.canRead()) {
                try (FileReader reader = new FileReader(file)) {
                    props.load(reader);
                }

                return DbKind.ofName(props.getProperty(KEYVALUE_DATASOURCE_PROP_NAME));
            }

            return DbKind.LEVEL_DB;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void generatedDbKindFile(DbKind dbKind, String databaseDir) {
        try {
            File file = new File(databaseDir, DB_KIND_PROPERTIES_FILE);
            Properties props = new Properties();
            props.setProperty(KEYVALUE_DATASOURCE_PROP_NAME, dbKind.name());
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "Generated dbKind. In order to follow selected db.");

                LoggerFactory.getLogger(KEYVALUE_DATASOURCE).info("Generated dbKind.properties file.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void validateDbKind(DbKind currentDbKind, String databaseDir, boolean databaseReset) {
        File dir = new File(databaseDir);

        if (dir.exists() && !dir.isDirectory()) {
            LoggerFactory.getLogger(KEYVALUE_DATASOURCE).error("database.dir should be a folder.");
            throw new IllegalStateException("database.dir should be a folder");
        }

        boolean databaseDirExists = dir.exists() && dir.isDirectory();

        if (!databaseDirExists || dir.list().length == 0) {
            KeyValueDataSource.generatedDbKindFile(currentDbKind, databaseDir);
            return;
        }

        DbKind prevDbKind = KeyValueDataSource.getDbKindValueFromDbKindFile(databaseDir);

        if (prevDbKind != currentDbKind) {
            if (databaseReset) {
                KeyValueDataSource.generatedDbKindFile(currentDbKind, databaseDir);
            } else {
                LoggerFactory.getLogger(KEYVALUE_DATASOURCE).warn("Use the flag --reset when running the application if you are using a different datasource. Also you can use the cli tool DbMigrate, in order to migrate data between databases.");
                throw new IllegalStateException("DbKind mismatch. You have selected " + currentDbKind.name() + " when the previous detected DbKind was " + prevDbKind.name() + ".");
            }
        }
    }
}
