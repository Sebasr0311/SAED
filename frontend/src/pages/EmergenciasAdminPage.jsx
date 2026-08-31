import React, { useState, useEffect } from 'react';
import { api } from '../lib/api';
import { useFetch } from '../lib/hooks';


const EmergenciasAdminPage = () => {
  const [planes, setPlanes] = useState([]);
  const [contactos, setContactos] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchEmergencias = async () => {
      try {
        const [planesRes, contactosRes] = await Promise.all([
          api.get('/api/emergencias/planes'),
          api.get('/api/emergencias/contactos')
        ]);
        setPlanes(planesRes.data);
        setContactos(contactosRes.data);
      } catch (error) {
        console.error('Error fetching emergencias data', error);
      } finally {
        setLoading(false);
      }
    };
    fetchEmergencias();
  }, []);

  if (loading) return <div>Cargando...</div>;

  return (
    <div className="emergencias-admin-container p-6">
      <h1 className="text-2xl font-bold mb-4">Módulo de Emergencias</h1>
      
      <div className="planes-emergencia mb-8">
        <h2 className="text-xl font-semibold mb-2">Planes de Emergencia</h2>
        {planes.length === 0 ? (
          <p>No hay planes de emergencia registrados.</p>
        ) : (
          <ul className="list-disc pl-5">
            {planes.map(plan => (
              <li key={plan.idPlanEmergencia} className="mb-2">
                <strong>{plan.titulo}</strong> ({plan.tipoContingencia})
                <br />
                <em>Puntos de encuentro:</em> {plan.puntosEncuentro}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="contactos-emergencia">
        <h2 className="text-xl font-semibold mb-2">Contactos de Emergencia</h2>
        {contactos.length === 0 ? (
          <p>No hay contactos de emergencia registrados.</p>
        ) : (
          <table className="min-w-full bg-white border border-gray-200">
            <thead>
              <tr>
                <th className="py-2 px-4 border-b">Entidad</th>
                <th className="py-2 px-4 border-b">Servicio</th>
                <th className="py-2 px-4 border-b">Teléfono</th>
                <th className="py-2 px-4 border-b">Dirección</th>
              </tr>
            </thead>
            <tbody>
              {contactos.map(contacto => (
                <tr key={contacto.idContactoEmergencia} className="text-center">
                  <td className="py-2 px-4 border-b">{contacto.entidad}</td>
                  <td className="py-2 px-4 border-b">{contacto.tipoServicio}</td>
                  <td className="py-2 px-4 border-b">{contacto.telefonoPrincipal}</td>
                  <td className="py-2 px-4 border-b">{contacto.direccion || 'N/A'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default EmergenciasAdminPage;


