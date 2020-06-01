package pt.ua.dicoogle.plugins.tnpacs;

import metal.utils.fileiterator.FileIterator;
import org.apache.commons.configuration.XMLConfiguration;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.dicoogle.sdk.PluginBase;
import pt.ua.dicoogle.sdk.StorageInputStream;
import pt.ua.dicoogle.sdk.StorageInterface;
import pt.ua.dicoogle.sdk.settings.ConfigurationHolder;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TNPStoragePlugin extends PluginBase implements StorageInterface {
    private static final Logger logger = LoggerFactory.getLogger(TNPStoragePlugin.class);

    private static final String name = "tnpacs-storage";
    private static final String scheme = "file";
    private boolean isEnabled = false;
    private File rootDir;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean enable() {
        isEnabled = true;
        return true;
    }

    @Override
    public boolean disable() {
        isEnabled = false;
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public Iterable<StorageInputStream> at(URI location, Object... parameters) {
        return new TNPIterable(location);
    }

    @Override
    public URI store(DicomObject dicomObject, Object... parameters) {
        if (!isEnabled) return null;

        Path filePath = Paths.get(rootDir.getAbsolutePath(), getDirectory(dicomObject).toString(), getBaseName(dicomObject));
        URI fileUri;
        try {
            fileUri = new URI(scheme, filePath.toString(), null);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }

        logger.debug("Trying to store in: {}", fileUri);

        File file = filePath.toFile();
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             DicomOutputStream dos = new DicomOutputStream(bos)) {
            logger.debug("Trying to store in: {}", file.getAbsolutePath());
            dos.writeDicomFile(dicomObject);
        } catch (IOException ex) {
            logger.error("Failed to store into {}", fileUri, ex);
            return null;
        }

        return fileUri;
    }

    @Override
    public URI store(DicomInputStream inputStream, Object... parameters) throws IOException {
        if (!isEnabled) return null;
        return store(inputStream.readDicomObject());
    }

    @Override
    public void remove(URI location) {
        if (!isEnabled) return;
        if (!location.getScheme().equals(scheme)) return;

        File file = new File(location);
        if (file.exists()) file.delete();
    }

    @Override
    public void setSettings(ConfigurationHolder xmlSettings) {
        super.setSettings(xmlSettings);
        XMLConfiguration config = xmlSettings.getConfiguration();

        config.setThrowExceptionOnMissing(true);

        Path rootDirPath = Paths.get("/data");
        try {
            rootDirPath = Paths.get(config.getString("rootdir"));
        } catch (NoSuchElementException ex) {
            config.setProperty("rootdir", rootDirPath.toString());
        }

        this.rootDir = rootDirPath.toFile();
    }

    private Path getDirectory(DicomObject dicomObject) {
        String institutionName = dicomObject.getString(Tag.InstitutionName);
        String modality = dicomObject.getString(Tag.Modality);
        String studyDate = dicomObject.getString(Tag.StudyDate);
        String accessionNumber = dicomObject.getString(Tag.AccessionNumber);
        String studyInstanceUid = dicomObject.getString(Tag.StudyInstanceUID);
        String patientName = dicomObject.getString(Tag.PatientName);

        if (institutionName == null || institutionName.equals("")) {
            institutionName = "UN_IN";
        }
        institutionName = institutionName.trim();
        institutionName = institutionName.replace(" ", "");
        institutionName = institutionName.replace(".", "");
        institutionName = institutionName.replace("&", "");


        if (modality == null || modality.equals("")) {
            modality = "UN_MODALITY";
        }

        if (studyDate == null || studyDate.equals("")) {
            studyDate = "UN_DATE";
        } else {
            try {
                String year = studyDate.substring(0, 4);
                String month = studyDate.substring(4, 6);
                String day = studyDate.substring(6, 8);

                studyDate = year + File.separator + month + File.separator + day;

            } catch (Exception e) {
                e.printStackTrace();
                studyDate = "UN_DATE";
            }
        }

        if (accessionNumber == null || accessionNumber.equals("")) {
            patientName = patientName.trim();
            patientName = patientName.replace(" ", "");
            patientName = patientName.replace(".", "");
            patientName = patientName.replace("&", "");

            if (patientName.equals("")) {
                if (studyInstanceUid == null || studyInstanceUid.equals("")) {
                    accessionNumber = "UN_ACC";
                } else {
                    accessionNumber = studyInstanceUid;
                }
            } else {
                accessionNumber = patientName;
            }
        }

        return Paths.get(institutionName, modality, studyDate, accessionNumber);
    }

    public static String getBaseName(DicomObject dicomObject) {
        String sopInstanceUid = dicomObject.getString(Tag.SOPInstanceUID);
        return String.format("%s.dcm", sopInstanceUid);
    }

    private class TNPIterable implements Iterable<StorageInputStream> {
        private final URI location;

        TNPIterable(URI location) {
            this.location = location;
        }

        @Override
        public Iterator<StorageInputStream> iterator() {
            if (!location.getScheme().equals(scheme)) return Collections.emptyIterator();
            File parent = new File(location.getSchemeSpecificPart());
            if (parent.isDirectory()) {
                return new TNPIterator(new FileIterator(parent));
            } else {
                List<File> files = new ArrayList<>(1);
                files.add(parent);
                return new TNPIterator(files.iterator());
            }
        }
    }

    private static class TNPIterator implements Iterator<StorageInputStream> {
        private final Iterator<File> it;

        TNPIterator(Iterator<File> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public StorageInputStream next() {
            File file = it.next();
            return new StorageInputStream() {
                @Override
                public URI getURI() {
                    return file.toURI();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new BufferedInputStream(new FileInputStream(file));
                }

                @Override
                public long getSize() {
                    return file.length();
                }
            };
        }
    }
}
