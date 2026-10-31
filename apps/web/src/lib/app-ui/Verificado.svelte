<script lang="ts">
	/** El sello de verificado del handoff (violeta con tilde blanca). */
	let { tam = 16 }: { tam?: number } = $props();
</script>

<svg
	width={tam}
	height={tam}
	viewBox="0 0 24 24"
	role="img"
	aria-label="Verificado"
	style="flex:none"
	><path
		d="M12 2l2.4 1.8 3-.2.9 2.9 2.5 1.7-1 2.8 1 2.8-2.5 1.7-.9 2.9-3-.2L12 22l-2.4-1.8-3 .2-.9-2.9-2.5-1.7 1-2.8-1-2.8 2.5-1.7.9-2.9 3 .2z"
		fill="#7456F1"
	/><path
		d="M8 12l3 3 5-6"
		stroke="#FFFFFF"
		stroke-width="2.2"
		fill="none"
		stroke-linecap="round"
		stroke-linejoin="round"
	/></svg
>
