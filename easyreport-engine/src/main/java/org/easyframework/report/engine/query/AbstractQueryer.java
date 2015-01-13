package org.easyframework.report.engine.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.easyframework.report.engine.data.ColumnType;
import org.easyframework.report.engine.data.ReportDataSource;
import org.easyframework.report.engine.data.ReportMetaDataCell;
import org.easyframework.report.engine.data.ReportMetaDataColumn;
import org.easyframework.report.engine.data.ReportMetaDataRow;
import org.easyframework.report.engine.data.ReportParameter;
import org.easyframework.report.engine.data.ReportQueryParamItem;
import org.easyframework.report.engine.exception.SQLQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

public abstract class AbstractQueryer {
	protected final Logger logger = LoggerFactory.getLogger(this.getClass());
	protected final ReportDataSource dataSource;
	protected final ReportParameter parameter;

	protected AbstractQueryer(ReportDataSource dataSource, ReportParameter parameter) {
		this.dataSource = dataSource;
		this.parameter = parameter;
	}

	public List<ReportMetaDataColumn> parseMetaDataColumns(String sqlText) {
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		List<ReportMetaDataColumn> columns = null;

		try {
			logger.info(sqlText);
			conn = this.getJdbcConnection();
			stmt = conn.prepareStatement(this.preprocessSqlText(sqlText));
			rs = stmt.executeQuery();
			ResultSetMetaData rsMataData = rs.getMetaData();
			int count = rsMataData.getColumnCount();
			columns = new ArrayList<ReportMetaDataColumn>(count);
			for (int i = 1; i <= count; i++) {
				ReportMetaDataColumn column = new ReportMetaDataColumn();
				column.setName(rsMataData.getColumnLabel(i));
				column.setDataType(rsMataData.getColumnTypeName(i));
				column.setWidth(rsMataData.getColumnDisplaySize(i));
				columns.add(column);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
			this.releaseJdbcResource(conn, stmt, rs);
		}

		return columns == null ? new ArrayList<ReportMetaDataColumn>(0) : columns;
	}

	public List<ReportQueryParamItem> parseQueryParamItems(String sqlText) {
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		HashSet<String> set = new HashSet<String>();
		List<ReportQueryParamItem> rows = new ArrayList<ReportQueryParamItem>();

		try {
			logger.info(sqlText);
			conn = this.getJdbcConnection();
			stmt = conn.prepareStatement(sqlText);
			rs = stmt.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				String text = rs.getString("text");
				name = (name == null) ? "" : name.trim();
				text = (text == null) ? "" : text.trim();
				if (!set.contains(name)) {
					set.add(name);
				}
				rows.add(new ReportQueryParamItem(name, text));
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} finally {
			this.releaseJdbcResource(conn, stmt, rs);
		}
		set.clear();
		return rows;
	}

	public List<ReportMetaDataRow> getMetaDataRows() {
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			logger.info(this.parameter.getSqlText());
			conn = this.getJdbcConnection();
			stmt = conn.prepareStatement(this.parameter.getSqlText());
			rs = stmt.executeQuery();
			return this.getMetaDataRows(rs, this.getSqlColumns(this.getMetaDataColumns()));
		} catch (Exception ex) {
			logger.error(String.format("SqlText:%s，Msg:%s", this.parameter.getSqlText(), ex));
			throw new SQLQueryException(ex);
		} finally {
			this.releaseJdbcResource(conn, stmt, rs);
		}
	}

	public List<ReportMetaDataColumn> getMetaDataColumns() {
		return JSON.parseArray(this.parameter.getMetaColumns(), ReportMetaDataColumn.class);
	}

	protected List<ReportMetaDataRow> getMetaDataRows(ResultSet rs, List<ReportMetaDataColumn> sqlColumns) throws SQLException {
		List<ReportMetaDataRow> rows = new ArrayList<ReportMetaDataRow>();
		while (rs.next()) {
			ReportMetaDataRow row = new ReportMetaDataRow();
			for (ReportMetaDataColumn column : sqlColumns) {
				Object value = rs.getObject(column.getName());
				if (column.getDataType().contains("BINARY")) {
					value = new String((byte[]) value);
				}
				row.add(new ReportMetaDataCell(column, column.getName(), value));
			}
			rows.add(row);
		}
		return rows;
	}

	protected void releaseJdbcResource(Connection conn, PreparedStatement stmt, ResultSet rs) {
		try {
			if (rs != null)
				rs.close();
			if (stmt != null)
				stmt.close();
			if (conn != null)
				conn.close();
		} catch (SQLException ex) {
			throw new RuntimeException("数据库资源释放异常", ex);
		}
	}

	protected List<ReportMetaDataColumn> getSqlColumns(List<ReportMetaDataColumn> metaDataColumns) {
		return metaDataColumns.stream()
				.filter(x -> x.getType() != ColumnType.COMPUTED)
				.collect(Collectors.toList());
	}

	/**
	 * 预处理获取报表列集合的sql语句，
	 * 在这里可以拦截全表查询等sql， 因为如果表的数据量很大，将会产生过多的内存消耗，甚至性能问题
	 * 
	 * @param sqlText 原sql语句
	 * @return 预处理后的sql语句
	 */
	protected String preprocessSqlText(String sqlText) {
		return sqlText;
	}

	/**
	 * 获取当前报表查询器的JDBC Connection对象
	 * 
	 * @return Connection
	 */
	protected abstract Connection getJdbcConnection();
}