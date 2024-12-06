

package FlinkCommerce;

import Deserializer.JSONValueDeserializationSchema;
import Dto.SalesPerCategory;
import Dto.SalesPerDay;
import Dto.SalesPerMonth;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import Dto.Transaction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.sql.Date;

public class DataStreamJob {
	private static String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
	private static String username = "postgres";
	private static String password = "postgres";
	public static void main(String[] args) throws Exception {

		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		String topic = "financial_transactions";
		KafkaSource<Transaction> source = KafkaSource.<Transaction>builder()
						.setBootstrapServers("localhost:9092")
								.setTopics(topic)
										.setGroupId("transactionId")
												.setStartingOffsets(OffsetsInitializer.earliest())
														.setValueOnlyDeserializer(new JSONValueDeserializationSchema())
																.build();
		DataStream<Transaction> transactionDataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka source");
		transactionDataStream.print();

		JdbcExecutionOptions execOption = new JdbcExecutionOptions.Builder()
				.withBatchSize(1000)
						.withBatchIntervalMs(200)
								.withMaxRetries(5)
										.build();
		JdbcConnectionOptions connOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
				.withUrl(jdbcUrl)
				.withDriverName("org.postgresql.Driver")
				.withUsername(username)
				.withPassword(password)
				.build();


		transactionDataStream.addSink(JdbcSink.sink(
				"CREATE TABLE IF NOT EXISTS transactions (" +
						"transaction_id VARCHAR(255) PRIMARY KEY, " +
						"product_id VARCHAR(255), " +
						"product_name VARCHAR(255), " +
						"product_category VARCHAR(255), " +
						"product_price DOUBLE PRECISION, " +
						"product_quantity INTEGER, " +
						"product_brand VARCHAR(255), " +
						"total_amount DOUBLE PRECISION, " +
						"currency VARCHAR(255), " +
						"customer_id VARCHAR(255), " +
						"transaction_date TIMESTAMP, " +
						"payment_method VARCHAR(255) " +
						")",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {
				},
				execOption,
				connOptions
		)).name("Create Transactions table Sink");

		//create sales_per_category table sink
		transactionDataStream.addSink(JdbcSink.sink(
				"CREATE TABLE IF NOT EXISTS sales_per_category (" +
						"transaction_date DATE, " +
						"category VARCHAR(255), " +
						"total_sales DOUBLE PRECISION, " +
						"PRIMARY KEY (transaction_date, category)" +
						")",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {

				},
				execOption,
				connOptions
		)).name("Create Sales Per Category Table");

		//create sales_per_day table sink
		transactionDataStream.addSink(JdbcSink.sink(
				"CREATE TABLE IF NOT EXISTS sales_per_day (" +
						"transaction_date DATE PRIMARY KEY, " +
						"total_sales DOUBLE PRECISION " +
						")",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {

				},
				execOption,
				connOptions
		)).name("Create Sales Per Day Table");

		//create sales_per_month table sink
		transactionDataStream.addSink(JdbcSink.sink(
				"CREATE TABLE IF NOT EXISTS sales_per_month (" +
						"year INTEGER, " +
						"month INTEGER, " +
						"total_sales DOUBLE PRECISION, " +
						"PRIMARY KEY (year, month)" +
						")",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {

				},
				execOption,
				connOptions
		)).name("Create Sales Per Month Table");

		transactionDataStream.addSink(JdbcSink.sink(
				"INSERT INTO transactions(transaction_id, product_id, product_name, product_category, product_price, " +
						"product_quantity, product_brand, total_amount, currency, customer_id, transaction_date, payment_method) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
						"ON CONFLICT (transaction_id) DO UPDATE SET " +
						"product_id = EXCLUDED.product_id, " +
						"product_name  = EXCLUDED.product_name, " +
						"product_category  = EXCLUDED.product_category, " +
						"product_price = EXCLUDED.product_price, " +
						"product_quantity = EXCLUDED.product_quantity, " +
						"product_brand = EXCLUDED.product_brand, " +
						"total_amount  = EXCLUDED.total_amount, " +
						"currency = EXCLUDED.currency, " +
						"customer_id  = EXCLUDED.customer_id, " +
						"transaction_date = EXCLUDED.transaction_date, " +
						"payment_method = EXCLUDED.payment_method " +
						"WHERE transactions.transaction_id = EXCLUDED.transaction_id",
				(JdbcStatementBuilder<Transaction>) (preparedStatement, transaction) -> {
					preparedStatement.setString(1, transaction.getTransactionId());
					preparedStatement.setString(2, transaction.getProductId());
					preparedStatement.setString(3, transaction.getProductName());
					preparedStatement.setString(4, transaction.getProductCategory());
					preparedStatement.setDouble(5, transaction.getProductPrice());
					preparedStatement.setInt(6, transaction.getProductQuantity());
					preparedStatement.setString(7, transaction.getProductBrand());
					preparedStatement.setDouble(8, transaction.getTotalAmount());
					preparedStatement.setString(9, transaction.getCurrency());
					preparedStatement.setString(10, transaction.getCustomerId());
					preparedStatement.setTimestamp(11, transaction.getTransactionDate());
					preparedStatement.setString(12, transaction.getPaymentMethod());

				},
				execOption,
				connOptions

		)).name("insert into transations table Sink");

		transactionDataStream.map(
						transaction -> {
							Date transactionDate = new Date(System.currentTimeMillis());
							String category = transaction.getProductCategory();
							double totalSales = transaction.getTotalAmount();
							return new SalesPerCategory(transactionDate, category, totalSales);
						}
				).keyBy(SalesPerCategory::getCategory)
				.reduce((salesPerCategory, t1) -> {
					salesPerCategory.setTotalSales(salesPerCategory.getTotalSales() + t1.getTotalSales());
					return salesPerCategory;
				}).addSink(JdbcSink.sink(
						"INSERT INTO sales_per_category(transaction_date, category, total_sales) " +
								"VALUES (?, ?, ?) " +
								"ON CONFLICT (transaction_date, category) DO UPDATE SET " +
								"total_sales = EXCLUDED.total_sales " +
								"WHERE sales_per_category.category = EXCLUDED.category " +
								"AND sales_per_category.transaction_date = EXCLUDED.transaction_date",
						(JdbcStatementBuilder<SalesPerCategory>) (preparedStatement, salesPerCategory) -> {
							preparedStatement.setDate(1, new Date(System.currentTimeMillis()));
							preparedStatement.setString(2, salesPerCategory.getCategory());
							preparedStatement.setDouble(3, salesPerCategory.getTotalSales());
						},
						execOption,
						connOptions
				)).name("Insert into sales per category table");

		transactionDataStream.map(
						transaction -> {
							Date transactionDate = new Date(System.currentTimeMillis());
							double totalSales = transaction.getTotalAmount();
							return new SalesPerDay(transactionDate, totalSales);
						}
				).keyBy(SalesPerDay::getTransactionDate)
				.reduce((salesPerDay, t1) -> {
					salesPerDay.setTotalSales(salesPerDay.getTotalSales() + t1.getTotalSales());
					return salesPerDay;
				}).addSink(JdbcSink.sink(
						"INSERT INTO sales_per_day(transaction_date, total_sales) " +
								"VALUES (?,?) " +
								"ON CONFLICT (transaction_date) DO UPDATE SET " +
								"total_sales = EXCLUDED.total_sales " +
								"WHERE sales_per_day.transaction_date = EXCLUDED.transaction_date",
						(JdbcStatementBuilder<SalesPerDay>) (preparedStatement, salesPerDay) -> {
							preparedStatement.setDate(1, new Date(System.currentTimeMillis()));
							preparedStatement.setDouble(2, salesPerDay.getTotalSales());
						},
						execOption,
						connOptions
				)).name("Insert into sales per day table");

		transactionDataStream.map(
						transaction -> {
							Date transactionDate = new Date(System.currentTimeMillis());
							int year = transactionDate.toLocalDate().getYear();
							int month = transactionDate.toLocalDate().getMonth().getValue();
							double totalSales = transaction.getTotalAmount();
							return new SalesPerMonth(year, month, totalSales);
						}
				).keyBy(SalesPerMonth::getMonth)
				.reduce((salesPerMonth, t1) -> {
					salesPerMonth.setTotalSales(salesPerMonth.getTotalSales() + t1.getTotalSales());
					return salesPerMonth;
				}).addSink(JdbcSink.sink(
						"INSERT INTO sales_per_month(year, month, total_sales) " +
								"VALUES (?,?,?) " +
								"ON CONFLICT (year, month) DO UPDATE SET " +
								"total_sales = EXCLUDED.total_sales " +
								"WHERE sales_per_month.year = EXCLUDED.year " +
								"AND sales_per_month.month = EXCLUDED.month ",
						(JdbcStatementBuilder<SalesPerMonth>) (preparedStatement, salesPerMonth) -> {
							preparedStatement.setInt(1, salesPerMonth.getYear());
							preparedStatement.setInt(2, salesPerMonth.getMonth());
							preparedStatement.setDouble(3, salesPerMonth.getTotalSales());
						},
						execOption,
						connOptions
				)).name("Insert into sales per month table");

		env.execute("Flink Ecommerce Realtime Streaming");
	}
}
