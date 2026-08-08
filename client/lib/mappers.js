function mapEntityId(item) {
  if (item == null || typeof item !== 'object') return item;
  if (Array.isArray(item)) return item.map(mapEntityId);

  const out = { ...item };

  if ('id' in out) {
    if (!('idResidente' in out)) out.idResidente = out.id;
    if (!('idUsuario' in out)) out.idUsuario = out.id;
    if (!('idApartamento' in out)) out.idApartamento = out.id;
    if (!('idContrato' in out)) out.idContrato = out.id;
    if (!('idMulta' in out)) out.idMulta = out.id;
    if (!('idPaquete' in out)) out.idPaquete = out.id;
    if (!('idVisita' in out)) out.idVisita = out.id;
    if (!('idAlerta' in out)) out.idAlerta = out.id;
    if (!('idParqueadero' in out)) out.idParqueadero = out.id;
    if (!('idAviso' in out)) out.idAviso = out.id;
    if (!('idTipoDocumento' in out) && !('idTipoDoc' in out)) out.idTipoDocumento = out.id;
    if (!('idVisitante' in out)) out.idVisitante = out.id;
    if (!('idCuota' in out)) out.idCuota = out.id;
    if (!('idPago' in out)) out.idPago = out.id;
  }

  // QuejaSugerencia: el backend expone idQueja y respuestaAdmin (no id ni respuesta).
  // Creamos los alias que el resto del frontend espera.
  if ('idQueja' in out && !('id' in out)) out.id = out.idQueja;
  if ('respuestaAdmin' in out && !('respuesta' in out)) out.respuesta = out.respuestaAdmin;

  if ('idTipoDoc' in out && !('idTipoDocumento' in out)) {
    out.idTipoDocumento = out.idTipoDoc;
  }
  if ('numeroApartamento' in out && !('apartamento' in out)) {
    out.apartamento = `Apto ${out.numeroApartamento}`;
  }

  return out;
}

export { mapEntityId };
