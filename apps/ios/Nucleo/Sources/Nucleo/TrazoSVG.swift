import CoreGraphics
import Foundation

/// Lee el atributo `d` de un `<path>` de SVG (los íconos del handoff) y arma un CGPath.
/// Soporta M L H V C S Q T A Z, absolutos y relativos.
public enum TrazoSVG {
    public static func camino(_ d: String) -> CGPath {
        let p = CGMutablePath()
        var l = Lexer(Array(d.utf8))
        var actual = CGPoint.zero
        var inicio = CGPoint.zero
        var ultimoControl: CGPoint?
        var ultimoCmd: UInt8 = 0
        var cmd: UInt8 = 0

        while true {
            l.saltarBlancos()
            guard let c = l.peek() else { break }
            if Lexer.esComando(c) { cmd = c; l.i += 1 } else if cmd == 0 { break }
            let rel = cmd >= 97
            let base = rel ? actual : .zero
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: base.x + x, y: base.y + y) }

            switch cmd | 0x20 {
            case UInt8(ascii: "m"):
                guard let x = l.num(), let y = l.num() else { return p }
                actual = pt(x, y); inicio = actual
                p.move(to: actual)
                cmd = rel ? UInt8(ascii: "l") : UInt8(ascii: "L")
                ultimoControl = nil
            case UInt8(ascii: "l"):
                guard let x = l.num(), let y = l.num() else { return p }
                actual = pt(x, y); p.addLine(to: actual); ultimoControl = nil
            case UInt8(ascii: "h"):
                guard let x = l.num() else { return p }
                actual = CGPoint(x: rel ? actual.x + x : x, y: actual.y); p.addLine(to: actual); ultimoControl = nil
            case UInt8(ascii: "v"):
                guard let y = l.num() else { return p }
                actual = CGPoint(x: actual.x, y: rel ? actual.y + y : y); p.addLine(to: actual); ultimoControl = nil
            case UInt8(ascii: "c"):
                guard let x1 = l.num(), let y1 = l.num(), let x2 = l.num(), let y2 = l.num(), let x = l.num(), let y = l.num() else { return p }
                let c1 = pt(x1, y1), c2 = pt(x2, y2)
                actual = pt(x, y)
                p.addCurve(to: actual, control1: c1, control2: c2)
                ultimoControl = c2
            case UInt8(ascii: "s"):
                guard let x2 = l.num(), let y2 = l.num(), let x = l.num(), let y = l.num() else { return p }
                let previoCubico = [UInt8(ascii: "c"), UInt8(ascii: "s")].contains(ultimoCmd | 0x20)
                let c1 = (previoCubico ? ultimoControl : nil).map { CGPoint(x: 2 * actual.x - $0.x, y: 2 * actual.y - $0.y) } ?? actual
                let c2 = pt(x2, y2)
                actual = pt(x, y)
                p.addCurve(to: actual, control1: c1, control2: c2)
                ultimoControl = c2
            case UInt8(ascii: "q"):
                guard let x1 = l.num(), let y1 = l.num(), let x = l.num(), let y = l.num() else { return p }
                let c1 = pt(x1, y1)
                actual = pt(x, y)
                p.addQuadCurve(to: actual, control: c1)
                ultimoControl = c1
            case UInt8(ascii: "t"):
                guard let x = l.num(), let y = l.num() else { return p }
                let previoCuad = [UInt8(ascii: "q"), UInt8(ascii: "t")].contains(ultimoCmd | 0x20)
                let c1 = (previoCuad ? ultimoControl : nil).map { CGPoint(x: 2 * actual.x - $0.x, y: 2 * actual.y - $0.y) } ?? actual
                actual = pt(x, y)
                p.addQuadCurve(to: actual, control: c1)
                ultimoControl = c1
            case UInt8(ascii: "a"):
                guard let rx = l.num(), let ry = l.num(), let rot = l.num(), let largo = l.bandera(), let barrido = l.bandera(),
                      let x = l.num(), let y = l.num() else { return p }
                let fin = pt(x, y)
                arco(p, desde: actual, hasta: fin, rx: rx, ry: ry, rotacion: rot, largo: largo, barrido: barrido)
                actual = fin
                ultimoControl = nil
            case UInt8(ascii: "z"):
                p.closeSubpath()
                actual = inicio
                ultimoControl = nil
            default:
                return p
            }
            ultimoCmd = cmd
            if cmd | 0x20 == UInt8(ascii: "z") { cmd = 0 }
        }
        return p
    }

    /// Arco elíptico de SVG (parametrización por extremos → centro, SVG 1.1 apéndice F.6).
    static func arco(_ p: CGMutablePath, desde a: CGPoint, hasta b: CGPoint, rx rx0: CGFloat, ry ry0: CGFloat,
                     rotacion: CGFloat, largo: Bool, barrido: Bool) {
        var rx = abs(rx0), ry = abs(ry0)
        if rx == 0 || ry == 0 || a == b { p.addLine(to: b); return }
        let phi = rotacion * .pi / 180
        let cosP = cos(phi), sinP = sin(phi)
        let dx = (a.x - b.x) / 2, dy = (a.y - b.y) / 2
        let x1 = cosP * dx + sinP * dy
        let y1 = -sinP * dx + cosP * dy
        let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var k = den == 0 ? 0 : sqrt(max(0, num / den))
        if largo == barrido { k = -k }
        let cx1 = k * rx * y1 / ry
        let cy1 = -k * ry * x1 / rx
        let cx = cosP * cx1 - sinP * cy1 + (a.x + b.x) / 2
        let cy = sinP * cx1 + cosP * cy1 + (a.y + b.y) / 2
        func angulo(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        }
        let t1 = angulo(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
        var dt = angulo((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
        if !barrido && dt > 0 { dt -= 2 * .pi }
        if barrido && dt < 0 { dt += 2 * .pi }
        let t = CGAffineTransform(translationX: cx, y: cy).rotated(by: phi).scaledBy(x: rx, y: ry)
        p.addArc(center: .zero, radius: 1, startAngle: t1, endAngle: t1 + dt, clockwise: !barrido, transform: t)
    }

    struct Lexer {
        let b: [UInt8]
        var i = 0
        init(_ b: [UInt8]) { self.b = b }

        static func esComando(_ c: UInt8) -> Bool {
            switch c | 0x20 {
            case UInt8(ascii: "m"), UInt8(ascii: "l"), UInt8(ascii: "h"), UInt8(ascii: "v"), UInt8(ascii: "c"),
                 UInt8(ascii: "s"), UInt8(ascii: "q"), UInt8(ascii: "t"), UInt8(ascii: "a"), UInt8(ascii: "z"):
                return c != UInt8(ascii: "e") && c != UInt8(ascii: "E")
            default: return false
            }
        }

        func peek() -> UInt8? { i < b.count ? b[i] : nil }

        mutating func saltarBlancos() {
            while i < b.count, b[i] == 32 || b[i] == 44 || b[i] == 9 || b[i] == 10 || b[i] == 13 { i += 1 }
        }

        mutating func bandera() -> Bool? {
            saltarBlancos()
            guard let c = peek(), c == UInt8(ascii: "0") || c == UInt8(ascii: "1") else { return nil }
            i += 1
            return c == UInt8(ascii: "1")
        }

        mutating func num() -> CGFloat? {
            saltarBlancos()
            let ini = i
            if let c = peek(), c == UInt8(ascii: "-") || c == UInt8(ascii: "+") { i += 1 }
            var punto = false, digitos = false
            while let c = peek() {
                if c >= 48 && c <= 57 { digitos = true; i += 1 }
                else if c == UInt8(ascii: ".") && !punto { punto = true; i += 1 }
                else { break }
            }
            if digitos, let c = peek(), c == UInt8(ascii: "e") || c == UInt8(ascii: "E") {
                let antes = i
                i += 1
                if let s = peek(), s == UInt8(ascii: "-") || s == UInt8(ascii: "+") { i += 1 }
                var exp = false
                while let c = peek(), c >= 48 && c <= 57 { exp = true; i += 1 }
                if !exp { i = antes }
            }
            guard digitos, let s = String(bytes: b[ini..<i], encoding: .ascii), let v = Double(s) else { i = ini; return nil }
            return CGFloat(v)
        }
    }
}
