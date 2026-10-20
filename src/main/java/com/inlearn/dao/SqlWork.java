package com.inlearn.dao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A unit of database work that runs on a connection supplied by {@link Database}.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface SqlWork<T> {

    T execute(Connection connection) throws SQLException;
}
