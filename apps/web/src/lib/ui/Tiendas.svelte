<script lang="ts">
	/**
	 * Las insignias oficiales de las tiendas (static/logos, sin modificar), con "Próximamente" arriba
	 * y sin link mientras no esté la app. 48 px en el celular, 44 px en la compu (Apple pide 40 de mínimo).
	 */
	let { tono = 'claro', centrado = true }: { tono?: 'claro' | 'oscuro'; centrado?: boolean } =
		$props();
</script>

<div class="tiendas {tono}" class:centrado>
	<span class="pronto">Próximamente</span>
	<div class="insignias">
		<img src="/logos/appstore.svg" alt="Próximamente en App Store" width="120" height="40" />
		<img src="/logos/googleplay.png" alt="Próximamente en Google Play" width="135" height="40" />
	</div>
</div>

<style>
	.tiendas {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.centrado {
		align-items: center;
	}
	.pronto {
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: #5c586b;
	}
	.oscuro .pronto {
		color: rgba(255, 255, 255, 0.6);
	}
	.insignias {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
	}
	.centrado .insignias {
		justify-content: center;
	}
	img {
		display: block;
		height: 48px;
		width: auto;
	}
	@media (min-width: 900px) {
		img {
			height: 44px;
		}
	}
</style>
