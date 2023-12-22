package tools.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.MarketArea;
import simulations.PowerMarkets;
import simulations.initialization.Settings;
/**
 * Basic file operations.
 */
public final class Operations {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(Operations.class.getName());

	/**
	 * Compress a file and deletes the original file
	 *
	 * @param inputFileName
	 */
	public static void compressFile(Path inputFile) {
		new Thread(() -> {
			final String threadName = "Compress file: " + inputFile;
			Thread.currentThread().setName(threadName);

			try {

				// Get file and name of file
				final String outputFileName = inputFile.getFileName() + ".zip";
				final Path outputFile = inputFile.getParent().resolve(outputFileName);
				final URI uri = URI.create("jar:file:" + outputFile.toUri().getPath());

				final FileSystem zipfs = FileSystems.newFileSystem(uri,
						Collections.singletonMap("create", "true"));
				final Path pathInZipfile = zipfs.getPath(inputFile.getFileName().toString());
				// copy a file into the zip file
				Files.move(inputFile, pathInZipfile, StandardCopyOption.REPLACE_EXISTING);
				zipfs.close();

			} catch (final Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}

		}).start();
	}

	/**
	 * Copy a directory
	 *
	 * @param quelle
	 * @param target_file
	 * @param delete
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void copyDir(File quelle, File target_file, boolean deleteSource)
			throws IOException {

		final File[] files = quelle.listFiles();
		File newFile = null;

		target_file.mkdirs();
		if (files != null) {
			for (final File file : files) {
				newFile = new File(target_file.getAbsolutePath()
						+ System.getProperty("file.separator") + file.getName());
				if (file.isDirectory()) {
					Operations.copyDir(file, newFile, false);
				} else {
					Operations.copyFile(file, newFile);
				}
			}
		}

		if (deleteSource) {
			Operations.deleteTree(quelle);
		}
	}

	/**
	 * Copy a File
	 *
	 * @param file
	 * @param target
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void copyFile(File file, File ziel) throws IOException {
		Files.copy(file.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Copy a given file by reading and writing all lines
	 *
	 * @param fileNameDest
	 *            complete path of source file
	 * @param fileNameSource
	 *            complete path of new file
	 */
	public static void copyXML(String fileNameDest, String fileNameSource) {

		try {
			// Create intermediate directories
			final File file = new File(fileNameDest);
			file.getParentFile().mkdirs();

			final FileWriter fileWriter = new FileWriter(fileNameDest);
			final BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
			final FileReader fileReader = new FileReader(new File(fileNameSource));
			final BufferedReader bufferedReader = new BufferedReader(fileReader);
			String zeile = bufferedReader.readLine();
			try {
				while (zeile != null) {
					bufferedWriter.write(zeile);
					bufferedWriter.newLine();
					zeile = bufferedReader.readLine();
				}
			} catch (final NullPointerException ex) {
				logger.error(ex.getMessage());
			}
			bufferedReader.close();
			bufferedWriter.close();

		} catch (final IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			final String[] children = dir.list();
			for (final String element : children) {
				Operations.deleteDir(new File(dir, element));
			}
		}
		return dir.delete();
	}

	/***
	 * Deletes the tree of a given path
	 *
	 * @param path
	 */
	public static void deleteTree(File path) {
		for (final File file : path.listFiles()) {
			if (file.isDirectory()) {
				Operations.deleteTree(file);
			}
			file.delete();
		}
		path.delete();
	}

	/** Copy xml files */
	public static void exportXMLFiles(Set<MarketArea> marketAreas) {
		// Copy base settings xml file
		final String scenarioPath = PowerMarkets.getScenarioPath();
		final String sourceDirectory = PowerMarkets.getSourceDirectory();
		final String settingsFile = PowerMarkets.getSettingsFile();
		final String userSettingsFile = PowerMarkets.getUserSettingsFile();
		final String settingsFileMulti = Settings.getMultiRunsFile();
		final String pathWorkspace = PowerMarkets.getPathWorkspace();
		final String projectName = PowerMarkets.getProjectName();
		final String paramDirectory = PowerMarkets.getParamDirectory();
		final String settingsFolder = PowerMarkets.getSettingsFolder();

		String fileNameDest = scenarioPath + Settings.getScenarioString() + File.separator
				+ sourceDirectory + settingsFile;
		String fileNameSource = pathWorkspace + File.separator + projectName + File.separator
				+ paramDirectory + settingsFolder + settingsFile;
		Operations.copyXML(fileNameDest, fileNameSource);

		fileNameDest = scenarioPath + Settings.getScenarioString() + File.separator
				+ sourceDirectory + userSettingsFile;
		fileNameSource = pathWorkspace + File.separator + projectName + File.separator
				+ paramDirectory + userSettingsFile;
		Operations.copyXML(fileNameDest, fileNameSource);

		if (settingsFileMulti != null) {
			final String fileNameMultiDest = scenarioPath + Settings.getScenarioString()
					+ File.separator + sourceDirectory + settingsFileMulti;
			final String fileNameMultiSource = pathWorkspace + File.separator + projectName
					+ File.separator + paramDirectory + settingsFolder + settingsFileMulti;
			Operations.copyXML(fileNameMultiDest, fileNameMultiSource);
		}

		// Copy agents xml file for each market area
		for (final MarketArea marketArea : marketAreas) {
			fileNameDest = scenarioPath + Settings.getScenarioString() + File.separator
					+ sourceDirectory + marketArea.getSettingsFileName();
			fileNameSource = pathWorkspace + File.separator + projectName + File.separator
					+ paramDirectory + settingsFolder + marketArea.getSettingsFileName();
			Operations.copyXML(fileNameDest, fileNameSource);
		}
	}

	/**
	 * Compress the folder given
	 * 
	 * @param folder
	 *            in file name
	 * @param zipFilePath
	 */
	public static void pack(final Path folder, final Path zipFilePath) {
		final String threadName = "Compress file to path " + zipFilePath;
		Thread.currentThread().setName(threadName);
		try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
				ZipOutputStream zos = new ZipOutputStream(fos)) {
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
					zos.putNextEntry(new ZipEntry(folder.relativize(dir).toString() + "/"));
					zos.closeEntry();
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
						throws IOException {
					zos.putNextEntry(new ZipEntry(folder.relativize(file).toString()));
					Files.copy(file, zos);
					zos.closeEntry();
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (final IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	public static void packFireAndForgot(final Path folder, final Path zipFilePath) {
		new Thread(() -> Operations.pack(folder, zipFilePath)).start();
	}

	public static void rarSrc() {

		// Copy base settings xml file
		final String scenarioPath = PowerMarkets.getScenarioPath();
		final String sourceDirectory = PowerMarkets.getSourceDirectory();

		final String pathWorkspace = PowerMarkets.getPathWorkspace();
		final String projectName = PowerMarkets.getProjectName();

		final String sourceFileDestination = scenarioPath + Settings.getScenarioString()
				+ File.separator + sourceDirectory + "src.zip";
		final String sourceFolderSource = pathWorkspace + projectName + File.separator + "src";

		packFireAndForgot(Paths.get(sourceFolderSource), Paths.get(sourceFileDestination));

	}
}