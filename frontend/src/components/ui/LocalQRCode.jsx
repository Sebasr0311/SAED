import React, { useRef, useState } from 'react';
import { QRCodeSVG, QRCodeCanvas } from 'qrcode.react';
import { QrCode, AlertCircle, Download, Check } from 'lucide-react';

/**
 * LocalQRCode — Componente canónico para generación y renderizado local de códigos QR.
 *
 * GAP-F7-04: Elimina la dependencia runtime de servicios externos (api.qrserver.com).
 * Genera el código QR directamente en el navegador del cliente mediante vector SVG
 * o elemento Canvas, garantizando disponibilidad offline, cero fugas de datos y
 * óptima resolución y accesibilidad.
 *
 * @param {string} value - Token o contenido exacto del código QR entregado por el backend.
 * @param {number} size - Tamaño en píxeles (ancho y alto). Default: 200.
 * @param {'L'|'M'|'Q'|'H'} level - Nivel de corrección de errores. Default: 'M'.
 * @param {boolean} includeMargin - Si incluye zona silenciosa (quiet zone). Default: true.
 * @param {string} alt - Descripción accesible del código.
 * @param {string} title - Título del elemento para lectores de pantalla y tooltip.
 * @param {string} className - Clases CSS del contenedor.
 * @param {function} onClick - Manejador de clic (por ej. para zoom).
 * @param {boolean} downloadable - Si muestra botón para descarga local en PNG.
 * @param {string} downloadFileName - Nombre de archivo al descargar.
 * @param {'svg'|'canvas'} renderAs - Formato de renderizado. Default: 'svg'.
 */
export function LocalQRCode({
  value,
  size = 200,
  level = 'M',
  includeMargin = true,
  alt = 'Código QR de acceso',
  title = 'Código QR de acceso',
  className = '',
  onClick,
  downloadable = false,
  downloadFileName = 'codigo-qr-acceso.png',
  renderAs = 'svg',
}) {
  const [copied, setCopied] = useState(false);
  const [renderError, setRenderError] = useState(false);
  const canvasRef = useRef(null);

  // 1. Estado de valor vacío o inválido (Fallback controlado y accesible)
  if (!value || typeof value !== 'string' || value.trim() === '') {
    return (
      <div
        role="img"
        aria-label="Código QR no disponible"
        className={`flex flex-col items-center justify-center p-3 rounded-lg border border-dashed border-muted-foreground/30 bg-muted/20 text-muted-foreground text-center select-none ${className}`}
        style={{ width: size, height: size }}
        onClick={onClick}
      >
        <AlertCircle className="w-6 h-6 mb-1 text-amber-500 opacity-80" />
        <span className="text-[11px] font-medium leading-tight">Sin código QR</span>
      </div>
    );
  }

  // 2. Manejo de descarga local (vía canvas sin peticiones de red)
  const handleDownload = (e) => {
    e.stopPropagation();
    try {
      if (canvasRef.current) {
        const url = canvasRef.current.toDataURL('image/png');
        const link = document.createElement('a');
        link.download = downloadFileName.endsWith('.png') ? downloadFileName : `${downloadFileName}.png`;
        link.href = url;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch (err) {
      console.error('Error al descargar código QR local:', err);
    }
  };

  return (
    <div
      className={`inline-flex flex-col items-center justify-center relative ${className}`}
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div
        className="flex items-center justify-center rounded-lg overflow-hidden bg-white p-1"
        role="img"
        aria-label={alt}
      >
        {renderAs === 'svg' ? (
          <QRCodeSVG
            value={value}
            size={size}
            level={level}
            includeMargin={includeMargin}
            title={title}
            aria-label={alt}
            bgColor="#FFFFFF"
            fgColor="#000000"
          />
        ) : (
          <QRCodeCanvas
            ref={canvasRef}
            value={value}
            size={size}
            level={level}
            includeMargin={includeMargin}
            aria-label={alt}
            bgColor="#FFFFFF"
            fgColor="#000000"
          />
        )}
      </div>

      {/* Si se solicita soporte de descarga pero se renderiza SVG, renderizamos un Canvas oculto como exportador */}
      {downloadable && renderAs === 'svg' && (
        <div style={{ display: 'none' }} aria-hidden="true">
          <QRCodeCanvas
            ref={canvasRef}
            value={value}
            size={size * 2} // Doble resolución para exportación nítida
            level={level}
            includeMargin={includeMargin}
            bgColor="#FFFFFF"
            fgColor="#000000"
          />
        </div>
      )}

      {downloadable && (
        <button
          type="button"
          onClick={handleDownload}
          title="Descargar código QR como PNG"
          aria-label="Descargar código QR como PNG"
          className="mt-2 inline-flex items-center gap-1 text-[11px] font-medium text-primary hover:text-primary/80 transition-colors py-1 px-2.5 rounded-md border border-border bg-card hover:bg-accent shadow-2xs"
        >
          {copied ? (
            <>
              <Check className="w-3.5 h-3.5 text-emerald-500" />
              <span>¡Descargado!</span>
            </>
          ) : (
            <>
              <Download className="w-3.5 h-3.5" />
              <span>Guardar Imagen</span>
            </>
          )}
        </button>
      )}
    </div>
  );
}

export default LocalQRCode;
