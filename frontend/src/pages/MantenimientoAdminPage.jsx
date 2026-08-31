import React, { useState, useEffect } from 'react';
import api from '../lib/api.js';

const MantenimientoAdminPage = () => {
    const [mantenimientos, setMantenimientos] = useState([]);
    const [activos, setActivos] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const mantRes = await api.get('/api/mantenimiento');
                const actRes = await api.get('/api/mantenimiento/activos');
                
                setMantenimientos(mantRes);
                setActivos(actRes);
            } catch (error) {
                console.error("Error fetching data", error);
            }
        };
        fetchData();
    }, []);

    return (
        <div className="p-6">
            <h1 className="text-2xl font-bold mb-4">Mantenimiento y Activos (Módulo Fantasma)</h1>
            
            <div className="mb-8">
                <h2 className="text-xl font-semibold mb-2">Activos</h2>
                <table className="min-w-full bg-white border border-gray-300">
                    <thead>
                        <tr>
                            <th className="border px-4 py-2">ID</th>
                            <th className="border px-4 py-2">Nombre</th>
                            <th className="border px-4 py-2">Property ID</th>
                        </tr>
                    </thead>
                    <tbody>
                        {activos.map(a => (
                            <tr key={a.id}>
                                <td className="border px-4 py-2">{a.id}</td>
                                <td className="border px-4 py-2">{a.nombre}</td>
                                <td className="border px-4 py-2">{a.propertyId}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div>
                <h2 className="text-xl font-semibold mb-2">Mantenimientos</h2>
                <table className="min-w-full bg-white border border-gray-300">
                    <thead>
                        <tr>
                            <th className="border px-4 py-2">ID</th>
                            <th className="border px-4 py-2">Activo ID</th>
                            <th className="border px-4 py-2">Descripción</th>
                            <th className="border px-4 py-2">Fecha</th>
                        </tr>
                    </thead>
                    <tbody>
                        {mantenimientos.map(m => (
                            <tr key={m.id}>
                                <td className="border px-4 py-2">{m.id}</td>
                                <td className="border px-4 py-2">{m.activoId}</td>
                                <td className="border px-4 py-2">{m.descripcion}</td>
                                <td className="border px-4 py-2">{m.fecha}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

export default MantenimientoAdminPage;
