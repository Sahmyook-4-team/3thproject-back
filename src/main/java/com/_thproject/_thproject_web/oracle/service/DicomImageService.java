import org.dcm4che3.data.Attributes;
// import org.dcm4che3.image.PhotometricInterpretation; // 직접 사용하지 않아도 됨
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.SafeClose;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream; // 추가
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class DicomImageService {

    public byte[] convertDicomToImage(InputStream dicomStream, String format) throws IOException {
        if (!format.equalsIgnoreCase("jpg") && !format.equalsIgnoreCase("png")) {
            throw new IllegalArgumentException("Unsupported image format. Only JPG and PNG are supported.");
        }

        BufferedImage image = null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(dicomStream)) { // InputStream 대신 ImageInputStream 사용
            Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
            if (!iter.hasNext()) {
                throw new IOException("No DICOM ImageReader found!");
            }
            ImageReader reader = iter.next();
            reader.setInput(iis);

            DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();
            // 필요한 경우 Window Center/Width 설정 (DICOM 파일에 따라 자동 적용될 수도 있음)
            // param.setWindowCenter(windowCenter);
            // param.setWindowWidth(windowWidth);

            image = reader.read(0, param); // 첫 번째 프레임(0)을 읽음
            reader.dispose();

        } catch (IOException e) {
            System.err.println("Error reading DICOM image: " + e.getMessage());
            throw e;
        }

        if (image == null) {
            throw new IOException("Failed to read DICOM image.");
        }

        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (format.equalsIgnoreCase("jpg")) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f); // Adjust quality as needed
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    // 기존 readDicomImage 와 applyWindowLevel 메서드는 필요 없어집니다.
    // 대신 ImageReader가 DICOM 헤더를 기반으로 픽셀 데이터를 적절히 처리합니다.
    
    // 이 메서드는 더 이상 필요하지 않습니다.
    // private BufferedImage readDicomImage(DicomInputStream dis) throws IOException { ... }
    
    // 이 메서드는 더 이상 필요하지 않습니다.
    // private void applyWindowLevel(BufferedImage image, Attributes attributes) { ... }
}