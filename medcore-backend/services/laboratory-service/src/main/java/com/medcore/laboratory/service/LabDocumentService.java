package com.medcore.laboratory.service;

/**
 * Documentos en papel del laboratorio: la etiqueta del tubo y el informe.
 *
 * <p>Son los dos papeles que existen de verdad en un laboratorio. La etiqueta
 * se pega en el tubo en el momento de la toma, y el informe es lo que el
 * paciente se lleva y lo que el medico archiva.
 */
public interface LabDocumentService {

    /**
     * Etiqueta de la muestra, en PNG, con el QR del codigo.
     *
     * <p>El tecnico escanea en lugar de teclear. Un digito mal teclado en un
     * codigo de muestra adjunta el resultado al paciente equivocado, que es el
     * error mas grave que puede cometer un laboratorio.
     *
     * @param lado lado del QR en pixeles
     */
    byte[] etiquetaPng(Long idMuestra, int lado);

    /**
     * Informe de resultados de una muestra, en PDF.
     *
     * <p>Solo de una muestra PROCESADA. Una muestra en proceso tiene resultados
     * a medias, y un informe con la mitad de las pruebas parece completo: quien
     * lo lea no puede saber que faltan valores.
     *
     * <p>Una muestra RECHAZADA no tiene informe, tiene motivo de rechazo.
     *
     * @throws com.medcore.commons.exception.MedCoreException si la muestra no
     *         esta en condiciones de tener informe
     */
    byte[] informePdf(Long idMuestra);
}
