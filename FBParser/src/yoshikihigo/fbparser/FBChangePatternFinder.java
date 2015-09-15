package yoshikihigo.fbparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import yoshikihigo.fbparser.db.DAO;
import yoshikihigo.fbparser.db.DAO.CHANGEPATTERN_SQL;
import yoshikihigo.fbparser.db.DAO.CHANGE_SQL;
import yoshikihigo.fbparser.db.DAO.CODE_SQL;

public class FBChangePatternFinder {

	public static void main(final String[] args) {

		FBParserConfig.initialize(args);
		final String trFile = FBParserConfig.getInstance()
				.getTRANSITIONRESULT();
		final String cpFile = FBParserConfig.getInstance().getCHANGEPATTERN();
		final String mcpFile = FBParserConfig.getInstance()
				.getMISSINGCHANGEPATTERN();
		final String bugFile = FBParserConfig.getInstance().getBUG();
		final DAO dao = DAO.getInstance();

		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(trFile),
						"JISAutoDetect"));
				final PrintWriter cpWriter = new PrintWriter(
						new BufferedWriter(new OutputStreamWriter(
								new FileOutputStream(cpFile), "UTF-8")));
				final Workbook book = new XSSFWorkbook();
				final OutputStream stream = new FileOutputStream(mcpFile)) {

			final String trTitle = reader.readLine();
			cpWriter.print(trTitle);
			cpWriter.println(", CHANGEPATTERN-ID, CHANGEPATTERN-SUPPORT");

			final Set<Integer> foundCPs = new HashSet<>();
			final Set<String> foundCodes = new HashSet<>();
			while (true) {
				final String lineText = reader.readLine();
				if (null == lineText) {
					break;
				}
				final Line line = new Line(lineText);
				if (line.status.startsWith("removed")
						&& (0 < line.startstartline) && (0 < line.startendline)) {
					final List<CHANGE_SQL> changes = dao.getChanges(
							line.endrev + 1, line.path);
					for (final CHANGE_SQL change : changes) {

						if (change.endline < line.startstartline) {
							continue;
						}

						if (line.startendline < change.startline) {
							continue;
						}

						// System.out.println("----------" + line.hash
						// + "----------");
						final List<CHANGEPATTERN_SQL> cps = dao
								.getChangePatterns(change.beforeHash,
										change.afterHash);
						for (final CHANGEPATTERN_SQL cp : cps) {
							// System.out.println(cp.id);
							cpWriter.print(lineText);
							cpWriter.print(", ");
							cpWriter.print(cp.id);
							cpWriter.print(", ");
							cpWriter.println(cp.support);
							foundCPs.add(cp.id);
							foundCodes.add(cp.beforeText);
						}
					}
				}
			}

			{
				final Sheet sheet = book.createSheet();
				book.setSheetName(0, "change-patterns");
				final Row titleRow = sheet.createRow(0);
				titleRow.createCell(0).setCellValue("RANKING");
				titleRow.createCell(1).setCellValue("FOUND-BY-FINDBUGS");
				titleRow.createCell(2).setCellValue("CHANGE-PATTERN-ID");
				titleRow.createCell(3).setCellValue("SUPPORT");
				titleRow.createCell(4).setCellValue("TEXT-BEFORE-CHANGE");
				titleRow.createCell(5).setCellValue("TEXT-AFTER-CHANGE");

				int currentRow = 1;
				int ranking = 1;
				final List<CHANGEPATTERN_SQL> cps = dao.getFixChangePatterns();
				for (final CHANGEPATTERN_SQL cp : cps) {

					if (cp.beforeText.isEmpty()) {
						continue;
					}

					final boolean foundByFindBugs = foundCPs.contains(cp.id);

					final Row dataRow = sheet.createRow(currentRow++);
					dataRow.createCell(0).setCellValue(ranking++);
					dataRow.createCell(1).setCellValue(
							foundByFindBugs ? "YES" : "NO");
					dataRow.createCell(2).setCellValue(cp.id);
					dataRow.createCell(3).setCellValue(cp.support);
					dataRow.createCell(4).setCellValue(cp.beforeText);
					dataRow.createCell(5).setCellValue(cp.afterText);

					final CellStyle style = book.createCellStyle();
					style.setWrapText(true);
					style.setFillPattern(CellStyle.SOLID_FOREGROUND);
					style.setFillForegroundColor(foundByFindBugs ? IndexedColors.ROSE
							.getIndex() : IndexedColors.WHITE.getIndex());
					style.setBorderBottom(XSSFCellStyle.BORDER_THIN);
					style.setBorderLeft(XSSFCellStyle.BORDER_THIN);
					style.setBorderRight(XSSFCellStyle.BORDER_THIN);
					style.setBorderTop(XSSFCellStyle.BORDER_THIN);
					dataRow.getCell(0).setCellStyle(style);
					dataRow.getCell(1).setCellStyle(style);
					dataRow.getCell(2).setCellStyle(style);
					dataRow.getCell(3).setCellStyle(style);
					dataRow.getCell(4).setCellStyle(style);
					dataRow.getCell(5).setCellStyle(style);

					int loc = Math.max(getLOC(cp.beforeText),
							getLOC(cp.afterText));
					dataRow.setHeight((short) (loc * dataRow.getHeight()));
				}
				sheet.autoSizeColumn(0);
				sheet.autoSizeColumn(1);
				sheet.autoSizeColumn(2);
				sheet.autoSizeColumn(3);
				sheet.autoSizeColumn(4);
				sheet.autoSizeColumn(5);
			}

//			{
//				final Sheet sheet = book.createSheet();
//				book.setSheetName(1, "code-pre-change");
//				final Row titleRow = sheet.createRow(0);
//				titleRow.createCell(0).setCellValue("RANKING");
//				titleRow.createCell(1).setCellValue("FOUND-BY-FINDBUGS");
//				titleRow.createCell(2).setCellValue("SUPPORT");
//				titleRow.createCell(3).setCellValue("TEXT-BEFORE-CHANGE");
//
//				int currentRow = 1;
//				int ranking = 1;
//				final List<CODE_SQL> codes = dao.getFixedCodes();
//				for (final CODE_SQL code : codes) {
//
//					if (code.text.isEmpty()) {
//						continue;
//					}
//
//					final boolean foundByFindBugs = foundCodes
//							.contains(code.text);
//
//					final Row dataRow = sheet.createRow(currentRow++);
//					dataRow.createCell(0).setCellValue(ranking++);
//					dataRow.createCell(1).setCellValue(
//							foundByFindBugs ? "YES" : "NO");
//					dataRow.createCell(2).setCellValue(code.support);
//					dataRow.createCell(3).setCellValue(code.text);
//
//					final CellStyle style = book.createCellStyle();
//					style.setWrapText(true);
//					style.setFillPattern(CellStyle.SOLID_FOREGROUND);
//					style.setFillForegroundColor(foundByFindBugs ? IndexedColors.ROSE
//							.getIndex() : IndexedColors.WHITE.getIndex());
//					style.setBorderBottom(XSSFCellStyle.BORDER_THIN);
//					style.setBorderLeft(XSSFCellStyle.BORDER_THIN);
//					style.setBorderRight(XSSFCellStyle.BORDER_THIN);
//					style.setBorderTop(XSSFCellStyle.BORDER_THIN);
//					dataRow.getCell(0).setCellStyle(style);
//					dataRow.getCell(1).setCellStyle(style);
//					dataRow.getCell(2).setCellStyle(style);
//					dataRow.getCell(3).setCellStyle(style);
//
//					int loc = getLOC(code.text);
//					dataRow.setHeight((short) (loc * dataRow.getHeight()));
//				}
//				sheet.autoSizeColumn(0);
//				sheet.autoSizeColumn(1);
//				sheet.autoSizeColumn(2);
//				sheet.autoSizeColumn(3);
//			}
			book.write(stream);

		} catch (final IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static int getLOC(final String text) {

		int count = 0;
		final String newline = System.lineSeparator();
		final Matcher matcher = Pattern.compile(newline).matcher(text);
		while (matcher.find()) {
			count++;
		}
		return count + 1;
	}
}

class Line {

	final String hash;
	final String type;
	final int rank;
	final int priority;
	final String status;
	final long startrev;
	final long endrev;
	final String path;
	final int startstartline;
	final int startendline;
	final int endstartline;
	final int endendline;

	Line(final String lineText) {
		final StringTokenizer tokenizer = new StringTokenizer(lineText, ", ");
		this.hash = tokenizer.nextToken();
		this.type = tokenizer.nextToken();
		this.rank = Integer.parseInt(tokenizer.nextToken());
		this.priority = Integer.parseInt(tokenizer.nextToken());
		this.status = tokenizer.nextToken();
		this.startrev = Long.parseLong(tokenizer.nextToken());
		this.endrev = Long.parseLong(tokenizer.nextToken());
		this.path = tokenizer.nextToken();
		final String startpos = tokenizer.nextToken();
		final String endpos = tokenizer.nextToken();
		if (startpos.equals("no-line-information")) {
			this.startstartline = 0;
			this.startendline = 0;
		} else {
			this.startstartline = Integer.parseInt(startpos.substring(0,
					startpos.indexOf('-')));
			this.startendline = Integer.parseInt(startpos.substring(startpos
					.lastIndexOf('-') + 1));
		}
		if (endpos.equals("no-line-information")) {
			this.endstartline = 0;
			this.endendline = 0;
		} else {
			this.endstartline = Integer.parseInt(endpos.substring(0,
					endpos.indexOf('-')));
			this.endendline = Integer.parseInt(endpos.substring(endpos
					.lastIndexOf('-') + 1));
		}
	}
}
