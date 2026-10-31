<script lang="ts">
	import { PUBLIC_PANEL } from '$app/env/public';

	/** Pie del sitio: grafito del splash con el logotipo, como el resto de las piezas oscuras. */
</script>

<footer>
	<div class="in">
		<div class="marca">
			<img src="/caigo-icon.png" alt="" width="44" height="44" loading="lazy" decoding="async" />
			<span>Caigo</span>
		</div>
		<p class="lema">Hacé planes. Conocé gente.</p>
		<nav aria-label="Pie de página">
			<a href={PUBLIC_PANEL}>Para lugares</a>
			<a href="/terminos">Términos</a>
			<a href="/privacidad">Privacidad</a>
			<a href="/arrepentimiento">Botón de arrepentimiento</a>
			<a href="mailto:hola@caigo.com.ar">hola@caigo.com.ar</a>
		</nav>
		<p class="chico">Mar del Plata y Villa Gesell · Una app de Grupo&nbsp;Vimo</p>
	</div>
</footer>

<style>
	footer {
		background:
			url(/grain.png), radial-gradient(70% 120% at 50% 0%, #3a3450 0%, #262233 50%, #17151f 100%);
		color: #faf9f6;
		padding: 48px 20px 40px;
	}
	.in {
		max-width: 1180px;
		margin: 0 auto;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.marca {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.marca img {
		width: 44px;
		height: 44px;
	}
	.marca span {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
	}
	.lema {
		font:
			700 20px 'Geist',
			sans-serif;
		letter-spacing: -0.2px;
	}
	nav {
		display: flex;
		flex-wrap: wrap;
		gap: 8px 20px;
		margin-top: 6px;
	}
	nav a {
		font-size: 14px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
	}
	nav a:hover {
		color: #ffffff;
	}
	.chico {
		font-size: 12px;
		font-weight: 600;
		color: #a8a3bd;
	}
</style>
