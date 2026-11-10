package de.htwberlin.dbtech.aufgaben.ue02;

/*
  @author Ingo Classen
 */

import de.htwberlin.dbtech.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("resource")
public class VersicherungJdbc implements IVersicherungJdbc {
    private static final Logger L = LoggerFactory.getLogger(VersicherungJdbc.class);
    private Connection connection;

    @Override
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    private Connection useConnection() {
        if (connection == null) {
            throw new DataException("Connection not set");
        }
        return connection;
    }

    @Override
    public List<String> kurzBezProdukte() {
        L.info("start");
        List<String> produkte = new ArrayList<>();
        String sql = "SELECT KurzBez FROM produkt ORDER BY ID";

        try (PreparedStatement pstmt = useConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                produkte.add(rs.getString("KurzBez"));
            }

        } catch (SQLException e) {
            throw new DataException(e);
        }

        L.info("ende");
        return produkte;
    }

    @Override
    public Kunde findKundeById(Integer id) {
        L.info("id: " + id);

        String sql = "SELECT ID, Name, Geburtsdatum FROM kunde WHERE ID = ?";

        try (PreparedStatement pstmt = useConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Kunde kunde = new Kunde(
                            rs.getInt("ID"),
                            rs.getString("Name"),
                            rs.getObject("Geburtsdatum", LocalDate.class)
                    );
                    L.info("ende");
                    return kunde;
                } else {
                    throw new KundeExistiertNichtException(id);
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    @Override
    public void createVertrag(Integer id, Integer produktId,
                              Integer kundenId, LocalDate versicherungsbeginn) {

        L.info("id: " + id);
        L.info("produktId: " + produktId);
        L.info("kundenId: " + kundenId);
        L.info("versicherungsbeginn: " + versicherungsbeginn);

        if (versicherungsbeginn.isBefore(LocalDate.now())) {
            throw new DatumInVergangenheitException(versicherungsbeginn);
        }

        LocalDate versicherungsende =
                versicherungsbeginn.plusYears(1).minusDays(1);

        // Vertrag existiert bereits Exception
        String vertragSql = "SELECT COUNT(*) FROM vertrag WHERE ID = ?";

        try (PreparedStatement pstmt =
                     useConnection().prepareStatement(vertragSql)) {

            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();

                if(rs.getInt(1) > 0) {
                    throw new VertragExistiertBereitsException(id);
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        // Produkt existiert nicht Exception
        String produktSql = "SELECT COUNT(*) FROM produkt WHERE ID = ?";

        try(PreparedStatement pstmt =
                useConnection().prepareStatement(produktSql)){
            pstmt.setInt(1, produktId);

            try(ResultSet rs = pstmt.executeQuery()){
                rs.next();

                if(rs.getInt(1) == 0){
                    throw new ProduktExistiertNichtException(produktId);
                }
            }
        }catch (SQLException e){
            throw new DataException(e);
        }

        // Kunde existiert nicht Exception
        String kundenSql = "SELECT COUNT(*) FROM kunde WHERE ID = ?";

        try(PreparedStatement pstmt =
                useConnection().prepareStatement(kundenSql)){
            pstmt.setInt(1, kundenId);
            try(ResultSet rs = pstmt.executeQuery()){
                rs.next();
                if(rs.getInt(1) == 0){
                    throw new KundeExistiertNichtException(kundenId);
                }
            }
        }catch(SQLException e){
            throw new DataException(e);
        }

        String sql = "INSERT INTO vertrag (ID, Produkt_FK, Kunde_FK, Versicherungsbeginn, Versicherungsende) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt =
                     useConnection().prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, produktId);
            pstmt.setInt(3, kundenId);
            pstmt.setObject(4, versicherungsbeginn);
            pstmt.setObject(5, versicherungsende);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            throw new DataException(e);
        }

        L.info("ende");
    }

    @Override
    public BigDecimal calcMonatsrate(Integer vertragsId) {
        L.info("vertragsId: " + vertragsId);

        BigDecimal monatsrate = BigDecimal.ZERO;

        String sql = "SELECT SUM(p.Preis) AS summe " +
                "FROM Vertrag v " +
                "JOIN Deckung d ON v.ID = d.Vertrag_FK " +
                "JOIN Deckungsbetrag db ON d.Deckungsart_FK = db.Deckungsart_FK " +
                "AND d.Deckungsbetrag = db.Deckungsbetrag " +
                "JOIN Deckungspreis p ON db.ID = p.Deckungsbetrag_FK " +
                "WHERE v.ID = ? " +
                "AND v.Versicherungsbeginn BETWEEN p.Gueltig_Von AND p.Gueltig_Bis";

        try (PreparedStatement pstmt = useConnection().prepareStatement(sql)) {
            pstmt.setInt(1, vertragsId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sum = rs.getBigDecimal("summe");
                    if (sum != null) {
                        monatsrate = sum;
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }

        L.info("ende");
        return monatsrate;
    }
}