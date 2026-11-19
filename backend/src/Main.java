
import dao.ConsultationDAO;
import model.Consultation;

public class Main {

    public static void main(String[] args) {

        ConsultationDAO consultationDAO = new ConsultationDAO();

        try {
            Consultation consultation = new Consultation(
                3,                       // Consultation ID (replace with actual ID)
                2,                       // Appointment ID
                "Allergic rhinitis",      // Updated diagnosis
                "Antihistamine for 5 days",
                "Patient improving",
                null
            );

            boolean updated =
                consultationDAO.updateConsultation(1, consultation);

            if (updated) {
                System.out.println("Consultation updated successfully!");
            } else {
                System.out.println("Consultation not found or access denied.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
